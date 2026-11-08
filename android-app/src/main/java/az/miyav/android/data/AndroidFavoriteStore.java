package az.miyav.android.data;

import android.content.Context;
import android.content.SharedPreferences;

import az.miyav.domain.repository.LocalFavoriteStore;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Guest favourites, kept only in this app's private storage. */
public final class AndroidFavoriteStore implements LocalFavoriteStore {
    private static final int LIMIT = 100;
    private final SharedPreferences preferences;

    public AndroidFavoriteStore(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences("miyav_guest_favorites", Context.MODE_PRIVATE);
    }

    @Override
    public synchronized Set<UUID> load() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (String value : preferences.getStringSet("ids", Set.of())) {
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // A corrupt entry is dropped on the next write.
            }
        }
        return ids;
    }

    @Override
    public synchronized void add(UUID listingId) {
        Set<String> ids = raw();
        if (ids.size() < LIMIT) {
            ids.add(listingId.toString());
            write(ids);
        }
    }

    @Override
    public synchronized void remove(UUID listingId) {
        Set<String> ids = raw();
        ids.remove(listingId.toString());
        write(ids);
    }

    @Override
    public synchronized void removeAll(Collection<UUID> listingIds) {
        Set<String> ids = raw();
        for (UUID id : listingIds) {
            ids.remove(id.toString());
        }
        write(ids);
    }

    private Set<String> raw() {
        return new HashSet<>(preferences.getStringSet("ids", Set.of()));
    }

    private void write(Set<String> ids) {
        preferences.edit().putStringSet("ids", ids).apply();
    }
}
