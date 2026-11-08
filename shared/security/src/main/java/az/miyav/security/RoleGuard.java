package az.miyav.security;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.domain.user.UserRole;

public final class RoleGuard {
    public void requireAdmin(UserRole role) {
        if (role != UserRole.ADMIN) {
            throw new AccessDeniedException("access.admin");
        }
    }

    public void requireSelf(java.util.UUID actorId, java.util.UUID ownerId) {
        if (actorId == null || !actorId.equals(ownerId)) {
            throw new AccessDeniedException("access.owner");
        }
    }
}
