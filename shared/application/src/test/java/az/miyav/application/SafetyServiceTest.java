package az.miyav.application;

import az.miyav.common.error.PersistenceException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.SafetyRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SafetyServiceTest {
    private final SafetyRepository repository = mock(SafetyRepository.class);
    private final SafetyService safety = new SafetyService(repository);
    private final AuthSession me = new AuthSession(UUID.randomUUID(), "me@example.com", "token", "refresh",
            Instant.parse("2026-11-09T12:00:00Z"), null);

    @Test
    void reportsNeedAReasonAndAnotherMember() {
        UUID other = UUID.randomUUID();
        assertEquals("report.reason", assertThrows(ValidationException.class,
                () -> safety.report(me, other, "  qısa ")).code());
        assertEquals("report.self", assertThrows(ValidationException.class,
                () -> safety.report(me, me.userId(), "Bu mətn kifayət qədər uzundur")).code());
        verify(repository, never()).report(any(), any(), anyString());

        safety.report(me, other, "  Saxta elan, pul istəyir  ");
        verify(repository).report(me, other, "Saxta elan, pul istəyir");
    }

    @Test
    void blockedMembersDisappearFromLists() {
        UUID blocked = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        when(repository.blocked(me)).thenReturn(Set.of());

        safety.block(me, blocked);

        List<UUID> shown = safety.visible(me, List.of(blocked, friend, blocked), id -> id);
        assertEquals(List.of(friend), shown);
        assertTrue(safety.isBlocked(me, blocked));
        verify(repository, times(1)).blocked(me);
    }

    @Test
    void unblockShowsTheMemberAgain() {
        UUID member = UUID.randomUUID();
        when(repository.blocked(me)).thenReturn(Set.of(member));
        assertEquals(List.of(), safety.visible(me, List.of(member), id -> id));

        safety.unblock(me, member);

        assertEquals(List.of(member), safety.visible(me, List.of(member), id -> id));
    }

    @Test
    void guestsAndFailedReadsSeeEverything() {
        UUID member = UUID.randomUUID();
        assertEquals(List.of(member), safety.visible(null, List.of(member), id -> id));
        when(repository.blocked(me)).thenThrow(new PersistenceException("network.unavailable"));
        assertEquals(List.of(member), safety.visible(me, List.of(member), id -> id));
    }

    @Test
    void membersCannotBlockThemselves() {
        assertEquals("report.self", assertThrows(ValidationException.class, () -> safety.block(me, me.userId())).code());
        verify(repository, never()).block(any(), any());
    }
}
