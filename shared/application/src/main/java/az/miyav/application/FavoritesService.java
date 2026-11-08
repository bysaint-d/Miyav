package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.repository.FavoriteRepository;
import az.miyav.domain.repository.LocalFavoriteStore;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Favourites for guests (kept on the device) and for signed-in users (kept on the account). Guest
 * favourites move to the account only through {@link #syncGuestFavorites}, which the UI calls after the
 * user agreed to it.
 */
public final class FavoritesService {
    private final LocalFavoriteStore local;
    private final FavoriteRepository remote;

    public FavoritesService(LocalFavoriteStore local, FavoriteRepository remote) {
        this.local = local;
        this.remote = remote;
    }

    public Set<UUID> ids(AuthSession viewer) {
        return viewer == null ? local.load() : remote.list(viewer);
    }

    public boolean isFavorite(AuthSession viewer, UUID listingId) {
        return ids(viewer).contains(listingId);
    }

    /** Saves or removes a favourite and returns the new state. */
    public boolean set(AuthSession viewer, UUID listingId, boolean favorite) {
        if (viewer == null) {
            if (favorite) {
                local.add(listingId);
            } else {
                local.remove(listingId);
            }
        } else if (favorite) {
            remote.add(viewer, listingId);
        } else {
            remote.remove(viewer, listingId);
        }
        return favorite;
    }

    public boolean toggle(AuthSession viewer, UUID listingId) {
        return set(viewer, listingId, !isFavorite(viewer, listingId));
    }

    /** Number of favourites saved on this device before sign-in that have not been moved yet. */
    public int guestCount() {
        return local.load().size();
    }

    /**
     * Moves device favourites to the account. Duplicates and listings that were removed meanwhile are
     * skipped by the server. On a network failure nothing is removed from the device.
     */
    public FavoriteRepository.MergeResult syncGuestFavorites(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
        Set<UUID> saved = local.load();
        if (saved.isEmpty()) {
            return FavoriteRepository.MergeResult.EMPTY;
        }
        FavoriteRepository.MergeResult result = remote.merge(session, saved);
        local.removeAll(saved);
        return result;
    }

    public void discardGuestFavorites() {
        local.removeAll(local.load());
    }

    /** Drops device favourites whose listing is no longer public. Account favourites are pruned by the server. */
    public void forgetUnavailable(AuthSession viewer, Collection<UUID> requested, List<Pet> available) {
        if (viewer != null) {
            return;
        }
        Set<UUID> gone = new HashSet<>(requested);
        for (Pet pet : available) {
            gone.remove(pet.id());
        }
        if (!gone.isEmpty()) {
            local.removeAll(gone);
        }
    }
}
