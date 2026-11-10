package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.AuthRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.validation.EmailValidator;
import az.miyav.domain.validation.PasswordValidator;
import az.miyav.security.MemorySessionStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    @Test
    void loginStoresTheRoleReturnedByTheProfile() {
        AuthRepository auth = mock(AuthRepository.class);
        UserRepository users = mock(UserRepository.class);
        MemorySessionStore sessions = new MemorySessionStore();
        UUID userId = UUID.randomUUID();
        AuthSession signedIn = session(userId, "secret-token");
        when(auth.signIn("a@example.com", "şifrə12345")).thenReturn(signedIn);
        when(users.findById(signedIn)).thenReturn(Optional.of(profile(userId, UserRole.ADMIN, false)));
        AuthService service = service(auth, users, sessions);

        AuthSession stored = service.login(" a@example.com ", "şifrə12345");

        assertEquals(UserRole.ADMIN, stored.role());
        assertEquals(UserRole.ADMIN, sessions.load().orElseThrow().role());
        assertFalse(stored.toString().contains("secret-token"));
    }

    @Test
    void suspendedProfileIsSignedOutAndNotStored() {
        AuthRepository auth = mock(AuthRepository.class);
        UserRepository users = mock(UserRepository.class);
        MemorySessionStore sessions = new MemorySessionStore();
        UUID userId = UUID.randomUUID();
        AuthSession signedIn = session(userId, "secret-token");
        when(auth.signIn(anyString(), anyString())).thenReturn(signedIn);
        when(users.findById(any())).thenReturn(Optional.of(profile(userId, UserRole.OWNER, true)));
        AuthService service = service(auth, users, sessions);

        assertThrows(AccessDeniedException.class, () -> service.login("a@example.com", "şifrə12345"));
        verify(auth).signOut(signedIn);
        assertFalse(sessions.load().isPresent());
    }

    @Test
    void registrationNeedsTheTermsAndRecordsTheirVersion() {
        AuthRepository auth = mock(AuthRepository.class);
        UserRepository users = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        AuthSession signedUp = session(userId, "secret-token");
        when(auth.signUp(anyString(), anyString(), anyString(), any(), anyString())).thenReturn(signedUp);
        when(users.findById(signedUp)).thenReturn(Optional.of(profile(userId, UserRole.OWNER, false)));
        AuthService service = service(auth, users, new MemorySessionStore());

        assertEquals("auth.terms_required", assertThrows(ValidationException.class,
                () -> service.register("a@example.com", "şifrə12345", "Aysel", null, false)).code());
        verify(auth, never()).signUp(anyString(), anyString(), anyString(), any(), anyString());

        service.register("a@example.com", "şifrə12345", "Aysel", null, true);
        verify(auth).signUp("a@example.com", "şifrə12345", "Aysel", null, az.miyav.domain.legal.Terms.VERSION);
    }

    @Test
    void invalidEmailDoesNotReachTheRepository() {
        AuthRepository auth = mock(AuthRepository.class);
        AuthService service = service(auth, mock(UserRepository.class), new MemorySessionStore());
        assertThrows(ValidationException.class, () -> service.login("bad", "şifrə12345"));
        verify(auth, never()).signIn(anyString(), anyString());
    }

    @Test
    void restoreRefreshesAnExpiredSession() {
        AuthRepository auth = mock(AuthRepository.class);
        UserRepository users = mock(UserRepository.class);
        MemorySessionStore sessions = new MemorySessionStore();
        UUID userId = UUID.randomUUID();
        AuthSession expired = new AuthSession(userId, "a@example.com", "old", "refresh", Instant.parse("2026-11-08T00:00:00Z"), UserRole.OWNER);
        AuthSession refreshed = new AuthSession(userId, "a@example.com", "new-token", "refresh-2", Instant.parse("2026-11-08T02:00:00Z"), null);
        sessions.save(expired);
        when(auth.refresh("refresh")).thenReturn(refreshed);
        when(users.findById(refreshed)).thenReturn(Optional.of(profile(userId, UserRole.OWNER, false)));
        AuthService service = new AuthService(auth, users, sessions, new EmailValidator(), new PasswordValidator(), () -> Instant.parse("2026-11-08T01:00:00Z"));

        AuthSession restored = service.restore().orElseThrow();

        assertEquals("new-token", restored.accessToken());
        assertEquals(UserRole.OWNER, restored.role());
        verify(auth).refresh("refresh");
    }

    @Test
    void restoreKeepsTheSessionWhenTheNetworkIsDown() {
        AuthRepository auth = mock(AuthRepository.class);
        UserRepository users = mock(UserRepository.class);
        MemorySessionStore sessions = new MemorySessionStore();
        AuthSession stored = new AuthSession(UUID.randomUUID(), "a@example.com", "token", "refresh", Instant.parse("2026-11-08T03:00:00Z"), UserRole.OWNER);
        sessions.save(stored);
        when(users.findById(any())).thenThrow(new az.miyav.common.error.PersistenceException("network.unavailable"));
        AuthService service = new AuthService(auth, users, sessions, new EmailValidator(), new PasswordValidator(), () -> Instant.parse("2026-11-08T01:00:00Z"));

        assertEquals("token", service.restore().orElseThrow().accessToken());
        assertEquals("token", sessions.load().orElseThrow().accessToken());
    }

    @Test
    void rejectedRefreshClearsTheSessionAndRequiresSignIn() {
        AuthRepository auth = mock(AuthRepository.class);
        MemorySessionStore sessions = new MemorySessionStore();
        sessions.save(new AuthSession(UUID.randomUUID(), "a@example.com", "old", "refresh", Instant.parse("2026-11-08T00:00:00Z"), UserRole.OWNER));
        when(auth.refresh("refresh")).thenThrow(new az.miyav.common.error.PersistenceException("auth.failed"));
        AuthService service = new AuthService(auth, mock(UserRepository.class), sessions, new EmailValidator(), new PasswordValidator(), () -> Instant.parse("2026-11-08T01:00:00Z"));

        AccessDeniedException expired = assertThrows(AccessDeniedException.class, service::requireSession);
        assertEquals("auth.session_expired", expired.code());
        assertFalse(sessions.load().isPresent());
    }

    @Test
    void authLinkForAnotherAddressIsRefused() {
        AuthRepository auth = mock(AuthRepository.class);
        UserRepository users = mock(UserRepository.class);
        MemorySessionStore sessions = new MemorySessionStore();
        UUID attacker = UUID.randomUUID();
        when(auth.fromTokens("t", "r", 3600)).thenReturn(new AuthSession(attacker, "attacker@example.com", "t", "r", Instant.parse("2026-11-08T02:00:00Z"), null));
        AuthService service = service(auth, users, sessions);

        assertThrows(AccessDeniedException.class, () -> service.completeLink("t", "r", 3600, "victim@example.com"));
        assertThrows(AccessDeniedException.class, () -> service.completeLink("t", "r", 3600, ""));
        assertFalse(sessions.load().isPresent());
        verify(users, never()).findById(any());
    }

    @Test
    void passwordChangeRequiresTheCurrentPassword() {
        AuthRepository auth = mock(AuthRepository.class);
        MemorySessionStore sessions = new MemorySessionStore();
        sessions.save(new AuthSession(UUID.randomUUID(), "a@example.com", "token", "refresh", Instant.parse("2026-11-08T03:00:00Z"), UserRole.OWNER));
        when(auth.signIn("a@example.com", "wrongpass1")).thenThrow(new az.miyav.common.error.PersistenceException("auth.failed"));
        AuthService service = new AuthService(auth, mock(UserRepository.class), sessions, new EmailValidator(), new PasswordValidator(), () -> Instant.parse("2026-11-08T01:00:00Z"));

        ValidationException wrong = assertThrows(ValidationException.class, () -> service.changePassword("wrongpass1", "newpassword9"));
        assertEquals("auth.current_password", wrong.code());
        verify(auth, never()).updatePassword(any(), anyString());
    }

    private static AuthService service(AuthRepository auth, UserRepository users, MemorySessionStore sessions) {
        return new AuthService(auth, users, sessions, new EmailValidator(), new PasswordValidator());
    }

    private static AuthSession session(UUID userId, String token) {
        return new AuthSession(userId, "a@example.com", token, "refresh", Instant.parse("2026-11-08T01:00:00Z"), null);
    }

    private static User profile(UUID userId, UserRole role, boolean suspended) {
        return new User(userId, "a@example.com", "Aysel Məmmədova", role, suspended, "Bakı", "", "", LocaleCode.AZ);
    }
}
