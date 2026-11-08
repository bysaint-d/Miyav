package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.repository.FavoriteRepository;
import az.miyav.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoritesServiceTest {
    private final MemoryLocalFavoriteStore local = new MemoryLocalFavoriteStore();
    private final MemoryFavorites remote = new MemoryFavorites();
    private final FavoritesService favorites = new FavoritesService(local, remote);
    private final AuthSession session = new AuthSession(UUID.randomUUID(), "a@example.com", "t", "r", Instant.MAX, UserRole.OWNER);

    @Test
    void guestFavoritesPersistOnTheDeviceOnly() {
        UUID listing = UUID.randomUUID();

        assertTrue(favorites.toggle(null, listing));
        assertTrue(favorites.isFavorite(null, listing));
        assertEquals(Set.of(listing), local.load());
        assertTrue(remote.saved.isEmpty(), "nothing reaches the account without sign-in");

        assertFalse(favorites.toggle(null, listing));
        assertTrue(local.load().isEmpty());
    }

    @Test
    void guestFavoritesSynchroniseAfterLoginWithoutDuplicates() {
        UUID kept = UUID.randomUUID();
        UUID alreadySaved = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        local.add(kept);
        local.add(alreadySaved);
        local.add(deleted);
        remote.saved.add(alreadySaved);
        remote.active.add(kept);
        remote.active.add(alreadySaved);

        FavoriteRepository.MergeResult result = favorites.syncGuestFavorites(session);

        assertEquals(1, result.added());
        assertEquals(1, result.alreadySaved());
        assertEquals(1, result.unavailable());
        assertEquals(Set.of(kept, alreadySaved), remote.saved);
        assertEquals(0, favorites.guestCount());
        assertEquals(Set.of(kept, alreadySaved), favorites.ids(session));
    }

    @Test
    void failedSyncKeepsFavoritesOnTheDevice() {
        UUID listing = UUID.randomUUID();
        local.add(listing);
        remote.offline = true;

        assertThrows(PersistenceException.class, () -> favorites.syncGuestFavorites(session));
        assertEquals(Set.of(listing), local.load());
    }

    @Test
    void syncNeedsAnAccount() {
        local.add(UUID.randomUUID());
        assertThrows(AccessDeniedException.class, () -> favorites.syncGuestFavorites(null));
        assertEquals(1, favorites.guestCount());
    }

    @Test
    void removedListingsAreForgottenFromGuestFavorites() {
        Pet live = DiscoveryServiceTest.dog(PetStatus.PUBLISHED);
        UUID gone = UUID.randomUUID();
        local.add(live.id());
        local.add(gone);

        favorites.forgetUnavailable(null, Set.of(live.id(), gone), List.of(live));

        assertEquals(Set.of(live.id()), local.load());
    }

    private static final class MemoryFavorites implements FavoriteRepository {
        final Set<UUID> saved = new LinkedHashSet<>();
        final Set<UUID> active = new LinkedHashSet<>();
        boolean offline;

        @Override
        public Set<UUID> list(AuthSession session) {
            return Set.copyOf(saved);
        }

        @Override
        public void add(AuthSession session, UUID listingId) {
            saved.add(listingId);
        }

        @Override
        public void remove(AuthSession session, UUID listingId) {
            saved.remove(listingId);
        }

        @Override
        public MergeResult merge(AuthSession session, Collection<UUID> listingIds) {
            if (offline) {
                throw new PersistenceException("network.unavailable");
            }
            int added = 0;
            int already = 0;
            int unavailable = 0;
            for (UUID id : listingIds) {
                if (saved.contains(id)) {
                    already++;
                } else if (active.contains(id)) {
                    saved.add(id);
                    added++;
                } else {
                    unavailable++;
                }
            }
            return new MergeResult(added, already, unavailable);
        }
    }
}
