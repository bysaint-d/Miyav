package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.domain.admin.PlatformOverview;
import az.miyav.domain.admin.UserSummary;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.map.MapPoint;
import az.miyav.domain.map.MapPointKind;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.repository.AdminInsightsRepository;
import az.miyav.domain.walk.WalkActivity;
import az.miyav.domain.walk.WalkRequest;
import az.miyav.domain.walk.WalkRequestStatus;
import az.miyav.security.RoleGuard;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Monitoring for the admin console: overview, walking requests, NearMe activity, map, members and pets.
 * Read-only; moderation actions stay in {@link AdminService}. Every method checks the administrator role
 * before calling the server, which checks it again.
 */
public final class AdminInsightsService {
    private final AdminInsightsRepository insights;
    private final RoleGuard access;

    public AdminInsightsService(AdminInsightsRepository insights, RoleGuard access) {
        this.insights = insights;
        this.access = access;
    }

    public PlatformOverview overview(AuthSession session) {
        requireAdmin(session);
        return insights.overview(session);
    }

    /** Requests whose current status is one of {@code statuses} (all when empty), matching the search text. */
    public List<WalkRequest> walkRequests(AuthSession session, Set<WalkRequestStatus> statuses, String search, Instant now) {
        requireAdmin(session);
        return insights.walkRequests(session, null).stream()
                .filter(request -> statuses.isEmpty() || statuses.contains(request.status(now)))
                .filter(request -> request.matches(search))
                .collect(Collectors.toList());
    }

    /** How many requests are in each current status, for the filter chips. */
    public Map<WalkRequestStatus, Integer> countByStatus(List<WalkRequest> requests, Instant now) {
        Map<WalkRequestStatus, Integer> counts = new EnumMap<>(WalkRequestStatus.class);
        for (WalkRequestStatus status : WalkRequestStatus.values()) {
            counts.put(status, 0);
        }
        for (WalkRequest request : requests) {
            counts.merge(request.status(now), 1, Integer::sum);
        }
        return counts;
    }

    public List<WalkActivity> walkActivity(AuthSession session) {
        requireAdmin(session);
        return insights.walkActivity(session);
    }

    /** Map points of the chosen kinds; live walks first so they are drawn on top. */
    public List<MapPoint> mapPoints(AuthSession session, Set<MapPointKind> kinds) {
        requireAdmin(session);
        return insights.mapPoints(session).stream()
                .filter(point -> kinds.contains(point.kind()))
                .sorted(Comparator.comparing((MapPoint point) -> point.kind() != MapPointKind.LIVE_WALK))
                .collect(Collectors.toList());
    }

    public List<UserSummary> users(AuthSession session) {
        requireAdmin(session);
        return insights.users(session);
    }

    public List<PetProfile> pets(AuthSession session) {
        requireAdmin(session);
        return insights.pets(session);
    }

    private void requireAdmin(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("access.admin");
        }
        access.requireAdmin(session.role());
    }
}
