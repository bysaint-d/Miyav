package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;

import java.util.Set;
import java.util.UUID;

/** Reports to moderators and private blocks between members. */
public interface SafetyRepository {
    void report(AuthSession session, UUID reportedId, String reason);

    Set<UUID> blocked(AuthSession session);

    void block(AuthSession session, UUID userId);

    void unblock(AuthSession session, UUID userId);
}
