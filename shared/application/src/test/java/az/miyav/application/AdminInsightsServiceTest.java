package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.admin.UserSummary;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.GeoPoint;
import az.miyav.domain.map.MapPoint;
import az.miyav.domain.map.MapPointKind;
import az.miyav.domain.repository.AdminInsightsRepository;
import az.miyav.domain.repository.AdminRepository;
import az.miyav.domain.repository.AdoptionRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.walk.CareService;
import az.miyav.domain.walk.WalkRequest;
import az.miyav.domain.walk.WalkRequestStatus;
import az.miyav.security.RoleGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminInsightsServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:30:00Z");
    private final AdminInsightsRepository repository = mock(AdminInsightsRepository.class);
    private final AdminInsightsService insights = new AdminInsightsService(repository, new RoleGuard());
    private final AuthSession admin = session(UserRole.ADMIN);

    @Test
    void ownersAndGuestsCannotOpenTheConsole() {
        assertThrows(AccessDeniedException.class, () -> insights.overview(session(UserRole.OWNER)));
        assertThrows(AccessDeniedException.class, () -> insights.users(null));
        assertThrows(AccessDeniedException.class, () -> insights.mapPoints(session(UserRole.SITTER), Set.of(MapPointKind.values())));
        verifyNoInteractions(repository);
    }

    @Test
    void walkingRequestsFilterByTheStatusPeopleSee() {
        WalkRequest pending = request("Aysel", "pending", NOW.plusSeconds(3600));
        WalkRequest walkingNow = request("Murad", "accepted", NOW.minusSeconds(600));
        WalkRequest done = request("Leyla", "completed", NOW.minusSeconds(86_400));
        when(repository.walkRequests(admin, null)).thenReturn(List.of(pending, walkingNow, done));

        assertEquals(List.of(walkingNow), insights.walkRequests(admin, Set.of(WalkRequestStatus.IN_PROGRESS), "", NOW));
        assertEquals(List.of(pending), insights.walkRequests(admin, Set.of(), "ays", NOW));
        Map<WalkRequestStatus, Integer> counts = insights.countByStatus(List.of(pending, walkingNow, done), NOW);
        assertEquals(1, counts.get(WalkRequestStatus.PENDING));
        assertEquals(1, counts.get(WalkRequestStatus.IN_PROGRESS));
        assertEquals(0, counts.get(WalkRequestStatus.ACCEPTED));
    }

    @Test
    void mapShowsOnlyTheChosenLayersWithLiveWalksOnTop() {
        GeoPoint baku = new GeoPoint(40.41, 49.87);
        MapPoint helper = MapPoint.of(MapPointKind.WALK_HELPER, UUID.randomUUID(), "Leyla", "", baku, true);
        MapPoint walk = MapPoint.of(MapPointKind.LIVE_WALK, UUID.randomUUID(), "Elmir", "", baku, false);
        MapPoint adoption = MapPoint.of(MapPointKind.ADOPTION, UUID.randomUUID(), "Mestan", "", baku, false);
        when(repository.mapPoints(admin)).thenReturn(List.of(helper, adoption, walk));

        assertEquals(List.of(walk, helper), insights.mapPoints(admin, Set.of(MapPointKind.LIVE_WALK, MapPointKind.WALK_HELPER)));
    }

    @Test
    void membersAreClassifiedByRecentActivity() {
        UserSummary fresh = user(NOW.minusSeconds(3 * 86_400), NOW.minusSeconds(3 * 86_400));
        UserSummary active = user(NOW.minusSeconds(90 * 86_400), NOW.minusSeconds(5 * 86_400));
        UserSummary quiet = user(NOW.minusSeconds(90 * 86_400), NOW.minusSeconds(60 * 86_400));

        assertEquals(UserSummary.Activity.NEW, fresh.activity(NOW));
        assertEquals(UserSummary.Activity.ACTIVE, active.activity(NOW));
        assertEquals(UserSummary.Activity.QUIET, quiet.activity(NOW));
        assertEquals("AM", fresh.initials());
    }

    @Test
    void moderatorsCanCancelOnlyUnfinishedRequests() {
        AdminRepository actions = mock(AdminRepository.class);
        AdminService service = new AdminService(mock(UserRepository.class), mock(PetRepository.class),
                mock(AdoptionRepository.class), actions, new RoleGuard());
        WalkRequest open = request("Aysel", "pending", NOW.plusSeconds(3600));
        WalkRequest done = request("Leyla", "completed", NOW.minusSeconds(86_400));

        service.cancelWalkRequest(admin, open, NOW, "spam");
        verify(actions).execute(admin, "booking", open.id(), "cancelled", "spam");

        assertThrows(ValidationException.class, () -> service.cancelWalkRequest(admin, done, NOW, ""));
        assertThrows(AccessDeniedException.class, () -> service.cancelWalkRequest(session(UserRole.OWNER), open, NOW, ""));
        verify(actions, never()).execute(any(), eq("booking"), eq(done.id()), anyString(), anyString());
    }

    private static WalkRequest request(String owner, String status, Instant start) {
        return new WalkRequest(UUID.randomUUID(), new WalkRequest.Participant(UUID.randomUUID(), owner),
                new WalkRequest.Participant(UUID.randomUUID(), "Walker"), "Boncuk", "dog", CareService.WALKING,
                start, start.plusSeconds(3600), status, BigDecimal.TEN, "hour", "Bakı", "", "", start);
    }

    private static UserSummary user(Instant joined, Instant lastActive) {
        return new UserSummary(UUID.randomUUID(), "Aysel Məmmədova", "Bakı", "", UserRole.OWNER, false, null,
                joined, 1, 0, 2, 0, lastActive);
    }

    private static AuthSession session(UserRole role) {
        return new AuthSession(UUID.randomUUID(), "a@example.com", "token", "refresh", Instant.MAX, role);
    }
}
