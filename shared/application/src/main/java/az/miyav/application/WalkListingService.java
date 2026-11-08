package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.common.time.TimeSource;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.WalkListingRepository;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkListingStatus;
import az.miyav.domain.walk.WalkOffer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pet walking listings: owners ask for a walk at a price per hour, walkers offer, the owner chooses.
 * The rules here give clear messages before a network call; RLS and the database functions enforce
 * the same rules on the server.
 */
public final class WalkListingService {
    private static final int MAX_MESSAGE = 500;
    private final WalkListingRepository listings;
    private final TimeSource time;

    public WalkListingService(WalkListingRepository listings, TimeSource time) {
        this.listings = listings;
        this.time = time;
    }

    /** The public board. Guests pass {@code null}. */
    public List<WalkListing> board(AuthSession viewer, PetSpecies species, String city, int limit, int offset) {
        if (limit < 1 || limit > 50 || offset < 0) {
            throw new ValidationException("validation.pet");
        }
        return listings.findOpen(species, city == null || city.isBlank() ? null : city, limit, offset, viewer);
    }

    public Optional<WalkListing> find(AuthSession viewer, UUID id) {
        return listings.findById(id, viewer);
    }

    public List<WalkListing> mine(AuthSession session) {
        requireSession(session);
        return listings.findMine(session);
    }

    public WalkListing publish(AuthSession session, WalkListingDetails details) {
        requireSession(session);
        details.requireBookable(time.now());
        return listings.create(session, details);
    }

    public WalkListing update(AuthSession session, WalkListing listing, WalkListingDetails details) {
        requireOwner(session, listing);
        if (listing.status() != WalkListingStatus.OPEN) {
            throw new ValidationException("walk.listing_closed");
        }
        details.requireBookable(time.now());
        return listings.update(session, listing.id(), details);
    }

    public void cancel(AuthSession session, WalkListing listing) {
        requireOwner(session, listing);
        if (!listing.status().active()) {
            throw new ValidationException("walk.listing_closed");
        }
        listings.cancel(session, listing.id());
    }

    public void complete(AuthSession session, WalkListing listing) {
        requireSession(session);
        if (!listing.ownedBy(session.userId()) && !listing.walkedBy(session.userId())) {
            throw new AccessDeniedException("access.owner");
        }
        if (!listing.completable(time.now())) {
            throw new ValidationException("walk.listing_closed");
        }
        listings.complete(session, listing.id());
    }

    /**
     * Sends or updates the walker's offer. {@code pricePerHour} is the walker's own price (for example 20 ₼
     * when the owner asked 10 ₼); null accepts the owner's price. Re-sending after a withdrawal is allowed.
     */
    public UUID offer(AuthSession session, WalkListing listing, BigDecimal pricePerHour, String message) {
        requireSession(session);
        if (listing.ownedBy(session.userId())) {
            throw new ValidationException("walk.own_listing");
        }
        if (!listing.acceptsOffers(time.now())) {
            throw new ValidationException("walk.listing_closed");
        }
        BigDecimal price = pricePerHour == null
                ? listing.details().pricePerHour()
                : WalkListingDetails.requireValidPrice(pricePerHour);
        String text = message == null ? "" : message.trim();
        if (text.length() > MAX_MESSAGE) {
            throw new ValidationException("validation.walk_notes");
        }
        return listings.offer(session, listing.id(), price, text);
    }

    public void withdraw(AuthSession session, WalkOffer offer) {
        requireSession(session);
        if (!offer.walkerId().equals(session.userId())) {
            throw new AccessDeniedException("access.owner");
        }
        if (!offer.pending()) {
            throw new ValidationException("walk.offer_closed");
        }
        listings.withdraw(session, offer.id());
    }

    public List<WalkOffer> offersFor(AuthSession session, WalkListing listing) {
        requireOwner(session, listing);
        return listings.offersFor(session, listing.id());
    }

    /** The offer the signed-in walker sent for this listing, if any. */
    public Optional<WalkOffer> myOfferFor(AuthSession session, WalkListing listing) {
        requireSession(session);
        return listings.myOffers(session).stream()
                .filter(offer -> offer.listingId().equals(listing.id()))
                .findFirst();
    }

    public List<WalkOffer> myOffers(AuthSession session) {
        requireSession(session);
        return listings.myOffers(session);
    }

    /** The owner accepts one walker (the others are declined) or declines this offer. */
    public void respond(AuthSession session, WalkListing listing, WalkOffer offer, boolean accept) {
        requireOwner(session, listing);
        if (!offer.listingId().equals(listing.id())) {
            throw new ValidationException("walk.offer_closed");
        }
        if (listing.status() != WalkListingStatus.OPEN) {
            throw new ValidationException("walk.listing_closed");
        }
        if (!offer.pending()) {
            throw new ValidationException("walk.offer_closed");
        }
        listings.respond(session, offer.id(), accept);
    }

    private static void requireOwner(AuthSession session, WalkListing listing) {
        requireSession(session);
        if (!listing.ownedBy(session.userId())) {
            throw new AccessDeniedException("access.owner");
        }
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }
}
