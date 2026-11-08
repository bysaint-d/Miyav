package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.admin.Broadcast;
import az.miyav.domain.admin.BroadcastAudience;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.repository.BroadcastRepository;
import az.miyav.security.RoleGuard;

import java.util.List;
import java.util.Objects;

/**
 * Push notifications from the admin console to all members, owners or walkers, optionally in one city.
 * Administrators only; the server checks the role again and limits sending to 10 per hour.
 */
public final class BroadcastService {
    private final BroadcastRepository broadcasts;
    private final RoleGuard access;

    public BroadcastService(BroadcastRepository broadcasts, RoleGuard access) {
        this.broadcasts = broadcasts;
        this.access = access;
    }

    public int audienceSize(AuthSession session, BroadcastAudience audience, String city) {
        requireAdmin(session);
        return broadcasts.audienceSize(session, Objects.requireNonNull(audience, "audience"), city(city));
    }

    public int send(AuthSession session, String title, String body, BroadcastAudience audience, String city) {
        requireAdmin(session);
        String heading = title == null ? "" : title.trim();
        String text = body == null ? "" : body.trim();
        if (heading.isEmpty() || heading.length() > Broadcast.MAX_TITLE || text.isEmpty() || text.length() > Broadcast.MAX_BODY) {
            throw new ValidationException("broadcast.text");
        }
        return broadcasts.send(session, heading, text, Objects.requireNonNull(audience, "audience"), city(city));
    }

    public List<Broadcast> recent(AuthSession session) {
        requireAdmin(session);
        return broadcasts.recent(session);
    }

    private static String city(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        if (!Pet.SUPPORTED_CITIES.contains(city)) {
            throw new ValidationException("validation.city");
        }
        return city;
    }

    private void requireAdmin(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("access.admin");
        }
        access.requireAdmin(session.role());
    }
}
