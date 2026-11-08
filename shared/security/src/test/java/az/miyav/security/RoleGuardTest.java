package az.miyav.security;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleGuardTest {
    private final RoleGuard guard = new RoleGuard();

    @Test
    void adminGateUsesTheServerRole() {
        assertDoesNotThrow(() -> guard.requireAdmin(UserRole.ADMIN));
        assertThrows(AccessDeniedException.class, () -> guard.requireAdmin(UserRole.OWNER));
        assertThrows(AccessDeniedException.class, () -> guard.requireAdmin(null));
    }
}
