package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.AppException;
import az.miyav.common.error.PersistenceException;
import az.miyav.common.error.ValidationException;
import az.miyav.common.time.SystemTimeSource;
import az.miyav.common.time.TimeSource;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.AuthRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.User;
import az.miyav.domain.text.TextRules;
import az.miyav.domain.validation.EmailValidator;
import az.miyav.domain.validation.PasswordValidator;
import az.miyav.security.SessionStore;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

public final class AuthService {
    private static final long REFRESH_MARGIN_SECONDS = 60;
    private final AuthRepository auth;
    private final UserRepository users;
    private final SessionStore sessions;
    private final EmailValidator emails;
    private final PasswordValidator passwords;
    private final TimeSource time;

    public AuthService(
            AuthRepository auth,
            UserRepository users,
            SessionStore sessions,
            EmailValidator emails,
            PasswordValidator passwords
    ) {
        this(auth, users, sessions, emails, passwords, new SystemTimeSource());
    }

    public AuthService(
            AuthRepository auth,
            UserRepository users,
            SessionStore sessions,
            EmailValidator emails,
            PasswordValidator passwords,
            TimeSource time
    ) {
        this.auth = auth;
        this.users = users;
        this.sessions = sessions;
        this.emails = emails;
        this.passwords = passwords;
        this.time = time;
    }

    public AuthSession register(String email, String password, String fullName) {
        return register(email, password, fullName, null);
    }

    public AuthSession register(String email, String password, String fullName, String redirectTo) {
        emails.validate(email);
        passwords.validate(password);
        TextRules.required(fullName, 2, 100, "validation.name");
        return establish(auth.signUp(email.trim(), password, fullName.trim(), redirectTo));
    }

    public AuthSession login(String email, String password) {
        emails.validate(email);
        if (password == null || password.isEmpty()) {
            throw new ValidationException("validation.password");
        }
        return establish(auth.signIn(email.trim(), password));
    }

    public synchronized void logout() {
        sessions.load().ifPresent(session -> {
            try {
                auth.signOut(session);
            } finally {
                sessions.clear();
            }
        });
    }

    public void requestPasswordReset(String email) {
        requestPasswordReset(email, null);
    }

    public void requestPasswordReset(String email, String redirectTo) {
        emails.validate(email);
        auth.requestPasswordReset(email.trim(), redirectTo);
    }

    public URI googleAuthorizeUri(String redirectTo) {
        return auth.googleAuthorizeUri(redirectTo);
    }

    /** Starts Google sign-in with PKCE. The caller keeps the verifier until the redirect comes back. */
    public GoogleSignIn beginGoogle(String redirectTo) {
        if (!auth.providerEnabled("google")) {
            throw new az.miyav.common.error.ConfigurationException("auth.google_unavailable");
        }
        String verifier = Pkce.verifier();
        return new GoogleSignIn(auth.googleAuthorizeUri(redirectTo, Pkce.challenge(verifier)), verifier);
    }

    public synchronized AuthSession completeGoogle(String authCode, String codeVerifier) {
        return establish(auth.exchangeCode(authCode, codeVerifier));
    }

    /** Deletes the account on the server, then forgets it on this device. */
    public synchronized void deleteAccount() {
        auth.deleteAccount(requireSession());
        sessions.clear();
    }

    /** Forgets the local session without a network call, used when the server already rejected it. */
    public synchronized void discardSession() {
        sessions.clear();
    }

    /**
     * Restores the stored session at start-up. Auth failures clear it; network failures keep it so the
     * app can open offline and retry later.
     */
    public synchronized Optional<AuthSession> restore() {
        Optional<AuthSession> stored = sessions.load();
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        AuthSession session = stored.get();
        try {
            if (session.expiringAt(time.now().plusSeconds(REFRESH_MARGIN_SECONDS))) {
                session = refreshed(session);
            }
            return Optional.of(establish(session));
        } catch (PersistenceException exception) {
            if ("network.unavailable".equals(exception.code())) {
                return Optional.of(session);
            }
            sessions.clear();
            return Optional.empty();
        } catch (AppException exception) {
            sessions.clear();
            return Optional.empty();
        }
    }

    /** Returns a usable session, refreshing the access token when it is about to expire. */
    public synchronized AuthSession requireSession() {
        AuthSession session = sessions.load().orElseThrow(() -> new AccessDeniedException("auth.session_expired"));
        if (!session.expiringAt(time.now().plusSeconds(REFRESH_MARGIN_SECONDS))) {
            return session;
        }
        AuthSession fresh = refreshed(session).withRole(session.role());
        sessions.save(fresh);
        return fresh;
    }

    public Optional<AuthSession> current() {
        return sessions.load();
    }

    /**
     * Completes an email confirmation or password-recovery link. The link is accepted only for the
     * address this device asked for, so a crafted link cannot sign the user into someone else's account.
     */
    public synchronized AuthSession completeLink(String accessToken, String refreshToken, long expiresInSeconds, String expectedEmail) {
        if (expectedEmail == null || expectedEmail.isBlank()) {
            throw new AccessDeniedException("auth.link_invalid");
        }
        AuthSession linked = auth.fromTokens(accessToken, refreshToken, expiresInSeconds);
        if (!normalize(linked.email()).equals(normalize(expectedEmail))) {
            throw new AccessDeniedException("auth.link_invalid");
        }
        return establish(linked);
    }

    /** Re-checks the current password, then sets the new one. */
    public synchronized AuthSession changePassword(String currentPassword, String newPassword) {
        AuthSession session = requireSession();
        passwords.validate(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new ValidationException("auth.same_password");
        }
        AuthSession verified;
        try {
            verified = auth.signIn(session.email(), currentPassword);
        } catch (PersistenceException exception) {
            if ("auth.failed".equals(exception.code())) {
                throw new ValidationException("auth.current_password");
            }
            throw exception;
        }
        auth.updatePassword(verified, newPassword);
        AuthSession stored = verified.withRole(session.role());
        sessions.save(stored);
        return stored;
    }

    /** Sets a new password for a session opened by a recovery link. */
    public synchronized void setRecoveredPassword(String newPassword) {
        passwords.validate(newPassword);
        auth.updatePassword(requireSession(), newPassword);
    }

    private AuthSession refreshed(AuthSession session) {
        if (session.refreshToken().isBlank()) {
            sessions.clear();
            throw new AccessDeniedException("auth.session_expired");
        }
        try {
            return auth.refresh(session.refreshToken());
        } catch (PersistenceException exception) {
            if ("network.unavailable".equals(exception.code())) {
                throw exception;
            }
            sessions.clear();
            throw new AccessDeniedException("auth.session_expired");
        }
    }

    private AuthSession establish(AuthSession signedIn) {
        User user = users.findById(signedIn).orElseThrow(() -> new PersistenceException("auth.profile_unavailable"));
        if (user.suspended()) {
            try {
                auth.signOut(signedIn);
            } finally {
                sessions.clear();
            }
            throw new AccessDeniedException("auth.suspended");
        }
        AuthSession session = signedIn.withRole(user.role());
        sessions.save(session);
        return session;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
