package az.miyav.application;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.WalkRepository;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.walk.WalkSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalkingServiceTest {
    private final AuthSession session = new AuthSession(UUID.randomUUID(), "a@example.com", "token", "refresh",
            Instant.parse("2026-11-08T03:00:00Z"), UserRole.OWNER);

    @Test
    void outOfAreaCoordinatesNeverReachTheServer() {
        WalkRepository walks = mock(WalkRepository.class);
        WalkingService service = new WalkingService(walks);
        ValidationException outside = assertThrows(ValidationException.class,
                () -> service.start(session, PetSpecies.DOG, "Boncuk", "", 39.237, -123.15));
        assertEquals("location.out_of_area", outside.code());
        assertThrows(ValidationException.class, () -> service.updateLocation(session, Double.NaN, 49.8));
        verify(walks, never()).start(any(), any(), anyString(), anyString(), anyDouble(), anyDouble());
        verify(walks, never()).updateLocation(any(), anyDouble(), anyDouble());
    }

    @Test
    void startRoundsTheDevicePositionAndValidatesText() {
        WalkRepository walks = mock(WalkRepository.class);
        WalkSession started = new WalkSession(UUID.randomUUID(), WalkSession.Status.ACTIVE, PetSpecies.DOG, "Boncuk", "",
                Instant.parse("2026-11-08T01:00:00Z"), Instant.parse("2026-11-08T01:10:00Z"));
        when(walks.start(eq(session), eq(PetSpecies.DOG), eq("Boncuk"), eq("Parkdayıq"), eq(40.40931), eq(49.86715)))
                .thenReturn(started);
        WalkingService service = new WalkingService(walks);

        assertEquals(started, service.start(session, PetSpecies.DOG, " Boncuk ", "Parkdayıq", 40.4093123, 49.8671512));
        assertThrows(ValidationException.class, () -> service.start(session, PetSpecies.DOG, "x".repeat(61), "", 40.4, 49.8));
        assertThrows(ValidationException.class, () -> service.start(session, null, "", "", 40.4, 49.8));
    }

    @Test
    void pauseAndEndUseTheServerStatusTransitions() {
        WalkRepository walks = mock(WalkRepository.class);
        WalkingService service = new WalkingService(walks);
        service.pause(session);
        service.end(session);
        verify(walks).setStatus(session, WalkSession.Status.PAUSED);
        verify(walks).setStatus(session, WalkSession.Status.ENDED);
    }
}
