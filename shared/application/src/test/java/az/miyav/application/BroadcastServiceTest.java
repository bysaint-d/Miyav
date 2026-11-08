package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.admin.BroadcastAudience;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.BroadcastRepository;
import az.miyav.domain.user.UserRole;
import az.miyav.security.RoleGuard;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BroadcastServiceTest {
    private final BroadcastRepository repository = mock(BroadcastRepository.class);
    private final BroadcastService service = new BroadcastService(repository, new RoleGuard());
    private final AuthSession admin = session(UserRole.ADMIN);

    @Test
    void onlyAdministratorsCanBroadcast() {
        assertThrows(AccessDeniedException.class,
                () -> service.send(session(UserRole.OWNER), "Salam", "Mətn", BroadcastAudience.ALL, null));
        assertThrows(AccessDeniedException.class, () -> service.send(null, "Salam", "Mətn", BroadcastAudience.ALL, null));
        verifyNoInteractions(repository);
    }

    @Test
    void textIsTrimmedAndLimited() {
        when(repository.send(admin, "Salam", "Yeni funksiya", BroadcastAudience.OWNERS, "Bakı")).thenReturn(3);
        assertEquals(3, service.send(admin, "  Salam ", " Yeni funksiya ", BroadcastAudience.OWNERS, "Bakı"));
        assertEquals("broadcast.text", assertThrows(ValidationException.class,
                () -> service.send(admin, " ", "Mətn", BroadcastAudience.ALL, null)).code());
        assertEquals("broadcast.text", assertThrows(ValidationException.class,
                () -> service.send(admin, "Salam", "x".repeat(501), BroadcastAudience.ALL, null)).code());
    }

    @Test
    void anEmptyCityMeansEveryCityAndUnknownCitiesAreRejected() {
        service.audienceSize(admin, BroadcastAudience.ALL, " ");
        verify(repository).audienceSize(admin, BroadcastAudience.ALL, null);
        assertEquals("validation.city", assertThrows(ValidationException.class,
                () -> service.audienceSize(admin, BroadcastAudience.ALL, "Gəncə")).code());
    }

    private static AuthSession session(UserRole role) {
        return new AuthSession(UUID.randomUUID(), "a@example.com", "t", "r", Instant.MAX, role);
    }
}
