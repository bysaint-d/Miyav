package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Favourites stored on the account. Every call needs a signed-in session. */
public interface FavoriteRepository {
    Set<UUID> list(AuthSession session);

    void add(AuthSession session, UUID listingId);

    void remove(AuthSession session, UUID listingId);

    /**
     * Adds the given listings to the account in one server call. The server skips listings that are
     * already saved and listings that are no longer active.
     */
    MergeResult merge(AuthSession session, Collection<UUID> listingIds);

    final class MergeResult {
        public static final MergeResult EMPTY = new MergeResult(0, 0, 0);
        private final int added;
        private final int alreadySaved;
        private final int unavailable;

        public MergeResult(int added, int alreadySaved, int unavailable) {
            this.added = added;
            this.alreadySaved = alreadySaved;
            this.unavailable = unavailable;
        }

        public int added() {
            return added;
        }

        public int alreadySaved() {
            return alreadySaved;
        }

        public int unavailable() {
            return unavailable;
        }
    }
}
