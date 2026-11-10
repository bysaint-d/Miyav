package az.miyav.domain.legal;

/**
 * The Terms of Service and Privacy Policy members accept. Raise {@link #VERSION} whenever the texts change
 * in a way members must agree to again; accounts with an older version are asked on their next sign-in.
 */
public final class Terms {
    /** Adds message moderation by administrators and paid premium listings. */
    public static final String VERSION = "2026-11-10";

    private Terms() {
    }

    public static boolean current(String acceptedVersion) {
        return VERSION.equals(acceptedVersion);
    }
}
