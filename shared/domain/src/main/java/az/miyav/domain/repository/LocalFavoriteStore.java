package az.miyav.domain.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Favourites a guest saved on this device. Nothing here leaves the device without the user's consent. */
public interface LocalFavoriteStore {
    Set<UUID> load();

    void add(UUID listingId);

    void remove(UUID listingId);

    void removeAll(Collection<UUID> listingIds);
}
