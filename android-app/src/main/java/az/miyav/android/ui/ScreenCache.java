package az.miyav.android.ui;

import android.util.LruCache;

import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.PublicProfile;

import java.util.List;
import java.util.UUID;

/**
 * What a screen already showed, so the next screen can draw it at once and refresh in the background:
 * a tapped listing on its detail page, a publisher on their profile page. Cleared on sign-out.
 */
public final class ScreenCache {
    private final LruCache<UUID, Pet> listings = new LruCache<>(200);
    private final LruCache<UUID, Publisher> publishers = new LruCache<>(60);

    public void remember(Pet pet) {
        if (pet != null) {
            listings.put(pet.id(), pet);
        }
    }

    public Pet listing(UUID id) {
        return listings.get(id);
    }

    public void remember(UUID userId, PublicProfile profile, List<Pet> listings) {
        if (profile != null) {
            publishers.put(userId, new Publisher(profile, List.copyOf(listings)));
        }
    }

    public Publisher publisher(UUID userId) {
        return publishers.get(userId);
    }

    public void clear() {
        listings.evictAll();
        publishers.evictAll();
    }

    public static final class Publisher {
        public final PublicProfile profile;
        public final List<Pet> listings;

        Publisher(PublicProfile profile, List<Pet> listings) {
            this.profile = profile;
            this.listings = listings;
        }
    }
}
