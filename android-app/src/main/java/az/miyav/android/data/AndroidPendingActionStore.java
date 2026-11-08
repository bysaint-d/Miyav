package az.miyav.android.data;

import android.content.Context;
import android.content.SharedPreferences;

import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.PendingActionStore;

import java.util.Optional;

/**
 * Keeps the action a guest tried before signing in. It survives process death so that confirming an
 * email in another app still returns the user to the same action. Stale actions are ignored.
 */
public final class AndroidPendingActionStore implements PendingActionStore {
    private static final long VALID_FOR_MS = 24L * 60 * 60 * 1000;
    private final SharedPreferences preferences;

    public AndroidPendingActionStore(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences("miyav_pending_action", Context.MODE_PRIVATE);
    }

    @Override
    public void save(PendingAction action) {
        preferences.edit()
                .putString("action", action.encode())
                .putLong("at", System.currentTimeMillis())
                .apply();
    }

    @Override
    public Optional<PendingAction> load() {
        if (System.currentTimeMillis() - preferences.getLong("at", 0) > VALID_FOR_MS) {
            return Optional.empty();
        }
        return Optional.ofNullable(PendingAction.decode(preferences.getString("action", "")));
    }

    @Override
    public void clear() {
        preferences.edit().clear().apply();
    }
}
