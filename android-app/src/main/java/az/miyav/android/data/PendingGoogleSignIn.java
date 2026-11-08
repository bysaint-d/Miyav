package az.miyav.android.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Holds the PKCE verifier of a Google sign-in started on this device. A redirect carrying a code is only
 * completed with this verifier, so a code delivered by a crafted link cannot open a session here.
 */
public final class PendingGoogleSignIn {
    private static final long VALID_FOR_MS = 10L * 60 * 1000;
    private final SharedPreferences preferences;

    public PendingGoogleSignIn(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences("miyav_google_pkce", Context.MODE_PRIVATE);
    }

    public void remember(String verifier) {
        preferences.edit()
                .putString("verifier", verifier)
                .putLong("at", System.currentTimeMillis())
                .apply();
    }

    /** Returns the verifier once and forgets it; empty when none was started or it is stale. */
    public String take() {
        long at = preferences.getLong("at", 0);
        String verifier = preferences.getString("verifier", "");
        preferences.edit().clear().apply();
        return System.currentTimeMillis() - at > VALID_FOR_MS ? "" : verifier;
    }
}
