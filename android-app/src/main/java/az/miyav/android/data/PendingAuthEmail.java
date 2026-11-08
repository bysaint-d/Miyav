package az.miyav.android.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers which address this device asked Supabase to email (signup or reset). Auth links are only
 * accepted for that address, which blocks crafted links that would sign the user into another account.
 */
public final class PendingAuthEmail {
    private static final long VALID_FOR_MS = 7L * 24 * 60 * 60 * 1000;
    private final SharedPreferences preferences;

    public PendingAuthEmail(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences("miyav_auth_link", Context.MODE_PRIVATE);
    }

    public void remember(String email) {
        preferences.edit()
                .putString("email", email == null ? "" : email.trim())
                .putLong("at", System.currentTimeMillis())
                .apply();
    }

    public String current() {
        long at = preferences.getLong("at", 0);
        if (System.currentTimeMillis() - at > VALID_FOR_MS) {
            return "";
        }
        return preferences.getString("email", "");
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
