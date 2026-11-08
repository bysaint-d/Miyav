package az.miyav.domain.repository;

import az.miyav.domain.admin.PlatformOverview;
import az.miyav.domain.admin.UserSummary;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.map.MapPoint;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.walk.WalkActivity;
import az.miyav.domain.walk.WalkRequest;

import java.util.List;

/**
 * Read-only views of the community for administrators. The server checks the administrator role on
 * every call and leaves out private content such as messages, contacts and exact positions.
 */
public interface AdminInsightsRepository {
    PlatformOverview overview(AuthSession session);

    /** Walking requests, optionally only those stored with the given status. */
    List<WalkRequest> walkRequests(AuthSession session, String storedStatus);

    List<WalkActivity> walkActivity(AuthSession session);

    List<MapPoint> mapPoints(AuthSession session);

    List<UserSummary> users(AuthSession session);

    List<PetProfile> pets(AuthSession session);
}
