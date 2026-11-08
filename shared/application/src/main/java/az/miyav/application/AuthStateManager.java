package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.AuthState;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.PendingActionStore;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.user.UserRole;
import az.miyav.security.AccessPolicy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one place clients ask who the user is, whether an action needs an account, and which action to
 * return to after sign-in. Screens never inspect sessions directly. Guests browse with the anonymous role;
 * protected actions go through {@link #request(PendingAction)}, and the server still enforces RLS.
 */
public final class AuthStateManager {
    public interface Listener {
        void onAuthStateChanged(AuthState state);
    }

    private final AuthService auth;
    private final PendingActionStore pending;
    private final AccessPolicy policy;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile AuthState state = AuthState.GUEST;

    public AuthStateManager(AuthService auth, PendingActionStore pending, AccessPolicy policy) {
        this.auth = auth;
        this.pending = pending;
        this.policy = policy;
    }

    public AuthState state() {
        return state;
    }

    public boolean signedIn() {
        return state == AuthState.AUTHENTICATED;
    }

    public Optional<AuthSession> session() {
        return signedIn() ? auth.current() : Optional.empty();
    }

    public UserRole role() {
        return session().map(AuthSession::role).orElse(null);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Adopts a stored session without a network call, for code that runs before {@link #restore()} such
     * as background services after a process restart. {@link #restore()} still validates it.
     */
    public AuthState prime() {
        if (state == AuthState.GUEST && auth.current().isPresent()) {
            set(AuthState.AUTHENTICATED);
        }
        return state;
    }

    /**
     * Restores a stored session at start-up. Never blocks browsing: without a usable session the app is
     * simply in guest mode, and a session the server rejected is reported as expired.
     */
    public AuthState restore() {
        boolean hadSession = auth.current().isPresent();
        try {
            Optional<AuthSession> restored = auth.restore();
            set(restored.isPresent() ? AuthState.AUTHENTICATED : hadSession ? AuthState.SESSION_EXPIRED : AuthState.GUEST);
        } catch (RuntimeException exception) {
            set(auth.current().isPresent() ? AuthState.AUTHENTICATED : AuthState.GUEST);
        }
        return state;
    }

    /**
     * Session to attach to public reads: the signed-in user's session, or {@code null} for the anonymous
     * role. An expired session downgrades to guest browsing instead of failing the read.
     */
    public AuthSession viewer() {
        if (!signedIn()) {
            return null;
        }
        try {
            return auth.requireSession();
        } catch (AccessDeniedException exception) {
            expire();
            return null;
        } catch (PersistenceException exception) {
            return auth.current().orElse(null);
        }
    }

    /** Session for a protected operation. Throws {@code auth.required} for guests. */
    public AuthSession requireSession() {
        if (!signedIn()) {
            throw new AccessDeniedException(state == AuthState.SESSION_EXPIRED ? "auth.session_expired" : "auth.required");
        }
        try {
            return auth.requireSession();
        } catch (AccessDeniedException exception) {
            expire();
            throw exception;
        }
    }

    /**
     * Gate for a protected action. When sign-in is required the action is remembered so the app can
     * return to it; the caller then opens the sign-in sheet.
     */
    public AccessPolicy.Decision request(PendingAction action) {
        AccessPolicy.Decision decision = policy.decide(state, role(), action.action());
        if (decision == AccessPolicy.Decision.SIGN_IN_REQUIRED) {
            pending.save(action);
        }
        return decision;
    }

    public AccessPolicy.Decision request(ProtectedAction action) {
        return request(PendingAction.of(action));
    }

    public AccessPolicy.Decision decide(ProtectedAction action) {
        return policy.decide(state, role(), action);
    }

    public void beginAuthentication() {
        if (!signedIn()) {
            set(AuthState.AUTHENTICATING);
        }
    }

    /** The user chose "later" or left the sign-in screen: back to guest browsing, nothing to resume. */
    public void cancelAuthentication() {
        pending.clear();
        if (state == AuthState.AUTHENTICATING) {
            set(AuthState.GUEST);
        }
    }

    public AuthSession login(String email, String password) {
        return authenticate(() -> auth.login(email, password));
    }

    public AuthSession register(String email, String password, String fullName, String redirectTo) {
        return authenticate(() -> auth.register(email, password, fullName, redirectTo));
    }

    public AuthSession completeGoogle(String authCode, String verifier) {
        return authenticate(() -> auth.completeGoogle(authCode, verifier));
    }

    public AuthSession completeLink(String accessToken, String refreshToken, long expiresIn, String expectedEmail) {
        return authenticate(() -> auth.completeLink(accessToken, refreshToken, expiresIn, expectedEmail));
    }

    /** Called when the server rejected the session. Browsing continues as a guest. */
    public void expire() {
        auth.discardSession();
        if (state != AuthState.GUEST) {
            set(AuthState.SESSION_EXPIRED);
        }
    }

    public void signOut() {
        try {
            auth.logout();
        } finally {
            pending.clear();
            set(AuthState.GUEST);
        }
    }

    public Optional<PendingAction> pending() {
        return pending.load();
    }

    /** Takes the remembered action once the user is signed in; empty for guests. */
    public Optional<PendingAction> takePending() {
        if (!signedIn()) {
            return Optional.empty();
        }
        Optional<PendingAction> action = pending.load();
        action.ifPresent(ignored -> pending.clear());
        return action;
    }

    /** Takes the remembered action only when it is the given one, so each screen resumes its own work. */
    public Optional<PendingAction> takePending(ProtectedAction action, String key, String value) {
        if (!signedIn()) {
            return Optional.empty();
        }
        Optional<PendingAction> stored = pending.load();
        if (stored.isPresent() && stored.get().matches(action, key, value)) {
            pending.clear();
            return stored;
        }
        return Optional.empty();
    }

    private AuthSession authenticate(java.util.function.Supplier<AuthSession> action) {
        AuthState previous = state;
        set(AuthState.AUTHENTICATING);
        try {
            AuthSession session = action.get();
            set(AuthState.AUTHENTICATED);
            return session;
        } catch (RuntimeException exception) {
            set(previous);
            throw exception;
        }
    }

    private void set(AuthState next) {
        if (state == next) {
            return;
        }
        state = next;
        for (Listener listener : listeners) {
            listener.onAuthStateChanged(next);
        }
    }
}
