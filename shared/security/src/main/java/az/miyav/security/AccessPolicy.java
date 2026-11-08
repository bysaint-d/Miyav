package az.miyav.security;

import az.miyav.domain.auth.AuthState;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.user.UserRole;

/**
 * Client-side authorization policy for protected actions. It only decides what the UI offers; the
 * server re-checks every protected write with Supabase Auth and RLS, so a bypassed client gains nothing.
 */
public final class AccessPolicy {
    public enum Decision { ALLOWED, SIGN_IN_REQUIRED, FORBIDDEN }

    public Decision decide(AuthState state, UserRole role, ProtectedAction action) {
        if (action == null) {
            return Decision.ALLOWED;
        }
        if (state != AuthState.AUTHENTICATED) {
            return Decision.SIGN_IN_REQUIRED;
        }
        if (action == ProtectedAction.ADMIN && role != UserRole.ADMIN) {
            return Decision.FORBIDDEN;
        }
        return Decision.ALLOWED;
    }
}
