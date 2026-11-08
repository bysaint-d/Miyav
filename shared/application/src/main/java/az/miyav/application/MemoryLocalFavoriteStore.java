package az.miyav.application;

import az.miyav.domain.repository.LocalFavoriteStore;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class MemoryLocalFavoriteStore implements LocalFavoriteStore {
    private final Set<UUID> ids = new LinkedHashSet<>();

    @Override
    public synchronized Set<UUID> load() {
        return Set.copyOf(ids);
    }

    @Override
    public synchronized void add(UUID listingId) {
        ids.add(listingId);
    }

    @Override
    public synchronized void remove(UUID listingId) {
        ids.remove(listingId);
    }

    @Override
    public synchronized void removeAll(Collection<UUID> listingIds) {
        ids.removeAll(listingIds);
    }
}
