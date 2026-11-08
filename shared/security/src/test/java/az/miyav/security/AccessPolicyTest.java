package az.miyav.security;

import az.miyav.domain.auth.AuthState;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessPolicyTest {
    private final AccessPolicy policy = new AccessPolicy();

    @Test
    void everyProtectedActionNeedsAnAccountUnlessSignedIn() {
        for (AuthState state : new AuthState[]{AuthState.GUEST, AuthState.AUTHENTICATING, AuthState.SESSION_EXPIRED}) {
            for (ProtectedAction action : ProtectedAction.values()) {
                assertEquals(AccessPolicy.Decision.SIGN_IN_REQUIRED, policy.decide(state, null, action), state + " " + action);
            }
        }
    }

    @Test
    void signedInUsersMayActButOnlyAdminsOpenTheDashboard() {
        assertEquals(AccessPolicy.Decision.ALLOWED, policy.decide(AuthState.AUTHENTICATED, UserRole.OWNER, ProtectedAction.APPLY_ADOPTION));
        assertEquals(AccessPolicy.Decision.FORBIDDEN, policy.decide(AuthState.AUTHENTICATED, UserRole.OWNER, ProtectedAction.ADMIN));
        assertEquals(AccessPolicy.Decision.FORBIDDEN, policy.decide(AuthState.AUTHENTICATED, UserRole.SITTER, ProtectedAction.ADMIN));
        assertEquals(AccessPolicy.Decision.ALLOWED, policy.decide(AuthState.AUTHENTICATED, UserRole.ADMIN, ProtectedAction.ADMIN));
    }
}
