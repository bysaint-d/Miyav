package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkOffer;

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

    /** Listings I posted and walks assigned to me. */
    List<WalkListing> findMine(AuthSession session);

    WalkListing create(AuthSession session, WalkListingDetails details);

    WalkListing update(AuthSession session, UUID listingId, WalkListingDetails details);

    void cancel(AuthSession session, UUID listingId);

    void complete(AuthSession session, UUID listingId);

    List<WalkOffer> offersFor(AuthSession session, UUID listingId);

    List<WalkOffer> myOffers(AuthSession session);

    /** Sends, updates or re-sends the walker's offer at their hourly price; it also appears in the chat. */
    UUID offer(AuthSession session, UUID listingId, BigDecimal pricePerHour, String message);

    void withdraw(AuthSession session, UUID offerId);

    void respond(AuthSession session, UUID offerId, boolean accept);
}
