package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkOffer;
import az.miyav.domain.walk.WalkerRating;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Walking listings and offers. Reads accept a {@code null} session for guests, who only see open,
 * upcoming listings; every write needs a signed-in session and is checked again by RLS.
 */
public interface WalkListingRepository {
    /** Open, upcoming listings, soonest first; {@code species} and {@code city} may be null. */
    List<WalkListing> findOpen(PetSpecies species, String city, int limit, int offset, AuthSession viewer);

    Optional<WalkListing> findById(UUID id, AuthSession viewer);

    /** Listings I posted, walks assigned to me and listings I offered on. */
    List<WalkListing> findMine(AuthSession session);

    /** Same as {@link #findMine(AuthSession)} when the caller already read its own offers. */
    default List<WalkListing> findMine(AuthSession session, List<WalkOffer> myOffers) {
        return findMine(session);
    }

    WalkListing create(AuthSession session, WalkListingDetails details);

    WalkListing update(AuthSession session, UUID listingId, WalkListingDetails details);

    void cancel(AuthSession session, UUID listingId);

    void complete(AuthSession session, UUID listingId);

    List<WalkOffer> offersFor(AuthSession session, UUID listingId);

    List<WalkOffer> myOffers(AuthSession session);

    /** Offers on several of my listings in one call, for badges on my requests. */
    default List<WalkOffer> offersForListings(AuthSession session, java.util.Collection<UUID> listingIds) {
        List<WalkOffer> offers = new java.util.ArrayList<>();
        for (UUID listingId : listingIds) {
            offers.addAll(offersFor(session, listingId));
        }
        return offers;
    }

    /** Sends, updates or re-sends the walker's offer at their hourly price; it also appears in the chat. */
    UUID offer(AuthSession session, UUID listingId, BigDecimal pricePerHour, String message);

    void withdraw(AuthSession session, UUID offerId);

    void respond(AuthSession session, UUID offerId, boolean accept);

    /** The owner's one-time 1–5 star rating of the walker on a completed walk. */
    void rate(AuthSession session, UUID listingId, int stars, String review);

    /** Average ratings of these walkers; walkers nobody rated yet are missing from the result. */
    List<WalkerRating> walkerRatings(AuthSession session, java.util.Collection<UUID> walkerIds);

    /** Photo of the pet on a listing the viewer can see; guests pass {@code null}. */
    default byte[] petPhoto(AuthSession viewer, String path) {
        throw new UnsupportedOperationException("petPhoto");
    }
}
