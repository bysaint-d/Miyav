package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.AuthState;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.repository.AuthRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.validation.EmailValidator;
import az.miyav.domain.validation.PasswordValidator;
import az.miyav.security.AccessPolicy;
import az.miyav.security.MemorySessionStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthStateManagerTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private final AuthRepository auth = mock(AuthRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MemorySessionStore sessions = new MemorySessionStore();
    private final MemoryPendingActionStore pending = new MemoryPendingActionStore();
    private final AuthStateManager manager = new AuthStateManager(
            new AuthService(auth, users, sessions, new EmailValidator(), new PasswordValidator(), () -> NOW),
            pending,
            new AccessPolicy());

    @Test
    void freshInstallOpensAsGuestWithoutTouchingTheAuthServer() {
        assertEquals(AuthState.GUEST, manager.restore());
        assertNull(manager.viewer());
        assertTrue(manager.session().isEmpty());
        verify(auth, never()).refresh(any());
    }

    @Test
    void guestCannotCreateListingsApplyOrMessage() {
        manager.restore();
        for (ProtectedAction action : List.of(ProtectedAction.CREATE_LISTING, ProtectedAction.APPLY_ADOPTION,
                ProtectedAction.SEND_MESSAGE, ProtectedAction.CONTACT_OWNER, ProtectedAction.SUBMIT_REPORT)) {
            assertEquals(AccessPolicy.Decision.SIGN_IN_REQUIRED, manager.decide(action));
        }
        AccessDeniedException denied = assertThrows(AccessDeniedException.class, manager::requireSession);
        assertEquals("auth.required", denied.code());
    }

    @Test
    void loginReturnsToTheActionTheGuestTried() {
        manager.restore();
        UUID petId = UUID.randomUUID();
        PendingAction apply = PendingAction.of(ProtectedAction.APPLY_ADOPTION, "petId", petId.toString());
        assertEquals(AccessPolicy.Decision.SIGN_IN_REQUIRED, manager.request(apply));
        assertTrue(manager.takePending().isEmpty(), "nothing resumes before sign-in");

        manager.beginAuthentication();
        assertEquals(AuthState.AUTHENTICATING, manager.state());
        signIn(UserRole.OWNER);

        assertEquals(AuthState.AUTHENTICATED, manager.state());
        assertTrue(manager.takePending(ProtectedAction.APPLY_ADOPTION, "petId", UUID.randomUUID().toString()).isEmpty(),
                "another listing does not consume the action");
        Optional<PendingAction> resumed = manager.takePending(ProtectedAction.APPLY_ADOPTION, "petId", petId.toString());
        assertEquals(Optional.of(apply), resumed);
        assertTrue(manager.takePending().isEmpty(), "an action resumes only once");
    }

    @Test
    void laterForgetsTheActionAndKeepsBrowsing() {
        manager.restore();
        manager.request(PendingAction.of(ProtectedAction.CREATE_LISTING));
        manager.beginAuthentication();
        manager.cancelAuthentication();
        assertEquals(AuthState.GUEST, manager.state());
        assertTrue(pending.load().isEmpty());
    }

    @Test
    void failedLoginStaysInTheSignInFlow() {
        manager.restore();
        manager.beginAuthentication();
        when(auth.signIn("a@example.com", "wrongpass1")).thenThrow(new PersistenceException("auth.failed"));
        assertThrows(PersistenceException.class, () -> manager.login("a@example.com", "wrongpass1"));
        assertEquals(AuthState.AUTHENTICATING, manager.state());
    }

    @Test
    void sessionRejectedAtStartIsReportedAsExpiredAndBrowsingContinues() {
        sessions.save(new AuthSession(UUID.randomUUID(), "a@example.com", "old", "refresh", NOW.minusSeconds(60), UserRole.OWNER));
        when(auth.refresh("refresh")).thenThrow(new PersistenceException("auth.failed"));

        assertEquals(AuthState.SESSION_EXPIRED, manager.restore());
        assertNull(manager.viewer(), "public reads fall back to the anonymous role");
        AccessDeniedException denied = assertThrows(AccessDeniedException.class, manager::requireSession);
        assertEquals("auth.session_expired", denied.code());
        assertEquals(AccessPolicy.Decision.SIGN_IN_REQUIRED, manager.decide(ProtectedAction.APPLY_ADOPTION));
    }

    @Test
    void sessionExpiringDuringUseDowngradesToGuestBrowsing() {
        manager.restore();
        signIn(UserRole.OWNER);
        AuthSession stored = sessions.load().orElseThrow();
        sessions.save(stored.withTokens("old", "refresh-2", NOW.minusSeconds(1)));
        when(auth.refresh("refresh-2")).thenThrow(new PersistenceException("auth.failed"));
        List<AuthState> seen = new ArrayList<>();
        manager.addListener(seen::add);

        assertNull(manager.viewer());
        assertEquals(AuthState.SESSION_EXPIRED, manager.state());
        assertFalse(sessions.load().isPresent());
        assertEquals(List.of(AuthState.SESSION_EXPIRED), seen);
    }

    @Test
    void adminRoutesNeedAnAdministratorAccount() {
        manager.restore();
        assertEquals(AccessPolicy.Decision.SIGN_IN_REQUIRED, manager.decide(ProtectedAction.ADMIN));
        signIn(UserRole.OWNER);
        assertEquals(AccessPolicy.Decision.FORBIDDEN, manager.request(ProtectedAction.ADMIN));
        assertTrue(pending.load().isEmpty(), "a forbidden action is not remembered");
        manager.signOut();
        signIn(UserRole.ADMIN);
        assertEquals(AccessPolicy.Decision.ALLOWED, manager.decide(ProtectedAction.ADMIN));
    }

    @Test
    void signOutReturnsToGuestMode() {
        manager.restore();
        signIn(UserRole.OWNER);
        manager.signOut();
        assertEquals(AuthState.GUEST, manager.state());
        assertNull(manager.viewer());
    }

    @Test
    void deletingTheAccountForgetsTheSessionAndReturnsToGuestMode() {
        manager.restore();
        signIn(UserRole.OWNER);
        AuthSession session = sessions.load().orElseThrow();
        manager.request(PendingAction.of(ProtectedAction.CREATE_LISTING));

        manager.deleteAccount();

        verify(auth).deleteAccount(session);
        assertEquals(AuthState.GUEST, manager.state());
        assertFalse(sessions.load().isPresent());
        assertTrue(pending.load().isEmpty());
    }

    @Test
    void failedDeletionKeepsTheUserSignedIn() {
        manager.restore();
        signIn(UserRole.ADMIN);
        org.mockito.Mockito.doThrow(new PersistenceException("account.admin")).when(auth).deleteAccount(any());

        PersistenceException failure = assertThrows(PersistenceException.class, manager::deleteAccount);

        assertEquals("account.admin", failure.code());
        assertEquals(AuthState.AUTHENTICATED, manager.state());
        assertTrue(sessions.load().isPresent());
    }

    @Test
    void guestsCannotDeleteAnAccount() {
        manager.restore();
        AccessDeniedException denied = assertThrows(AccessDeniedException.class, manager::deleteAccount);
        assertEquals("auth.required", denied.code());
        verify(auth, never()).deleteAccount(any());
    }

    @Test
    void googleSignInUsesPkceAndEndsAuthenticated() {
        manager.restore();
        UUID userId = UUID.randomUUID();
        AuthSession google = new AuthSession(userId, "g@example.com", "token", "refresh", NOW.plusSeconds(3600), null);
        when(auth.exchangeCode("code-1", "verifier-1")).thenReturn(google);
        when(users.findById(google)).thenReturn(Optional.of(profile(userId, UserRole.OWNER)));

        manager.completeGoogle("code-1", "verifier-1");

        assertEquals(AuthState.AUTHENTICATED, manager.state());
        assertEquals(userId, manager.viewer().userId());
    }

    private void signIn(UserRole role) {
        UUID userId = UUID.randomUUID();
        AuthSession signedIn = new AuthSession(userId, "a@example.com", "token-" + userId, "refresh", NOW.plusSeconds(3600), null);
        when(auth.signIn("a@example.com", "şifrə12345")).thenReturn(signedIn);
        when(users.findById(signedIn)).thenReturn(Optional.of(profile(userId, role)));
        manager.login("a@example.com", "şifrə12345");
    }

    private static User profile(UUID userId, UserRole role) {
        return new User(userId, "a@example.com", "Aysel Məmmədova", role, false, "Bakı", "", "", LocaleCode.AZ);
    }
}
