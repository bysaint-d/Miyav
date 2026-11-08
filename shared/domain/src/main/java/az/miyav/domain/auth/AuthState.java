package az.miyav.domain.auth;

/** Where the user stands with their account. Browsing never depends on it; protected actions do. */
public enum AuthState {
    GUEST,
    AUTHENTICATING,
    AUTHENTICATED,
    SESSION_EXPIRED;

    public boolean signedIn() {
        return this == AUTHENTICATED;
    }
}
