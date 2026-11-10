package az.miyav.domain.walk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything a member is part of in pet walking, from their side: requests they posted (with how many offers
 * wait for an answer), offers they sent (with the answer) and the agreed walks either way.
 */
public final class MyWalks {
    /** Where a walk stands for this member; drives the badge on each card. */
    public enum Stage {
        /** My open request without offers yet. */
        WAITING_FOR_OFFERS,
        /** My open request with offers I have not answered. */
        OFFERS_TO_REVIEW,
        /** I sent an offer; the owner has not answered. */
        OFFER_SENT,
        /** Agreed and not done yet, as owner or walker. */
        AGREED,
        /** My offer was declined or the owner chose someone else. */
        OFFER_DECLINED,
        /** I withdrew my offer. */
        OFFER_WITHDRAWN,
        COMPLETED,
        CANCELLED,
        /** Open but its start time has passed without an agreement. */
        EXPIRED
    }

    private final UUID me;
    private final List<WalkListing> listings;
    private final Map<UUID, WalkOffer> myOffers;
    private final Map<UUID, Integer> pendingOffers;

    /**
     * {@code myOffers} maps a listing id to the offer I sent on it; {@code pendingOffers} maps my own open
     * listings to the number of offers waiting for my answer.
     */
    public MyWalks(UUID me, List<WalkListing> listings, Map<UUID, WalkOffer> myOffers, Map<UUID, Integer> pendingOffers) {
        this.me = me;
        this.listings = List.copyOf(listings);
        this.myOffers = Map.copyOf(myOffers);
        this.pendingOffers = Map.copyOf(pendingOffers);
    }

    public static MyWalks empty(UUID me) {
        return new MyWalks(me, List.of(), Map.of(), Map.of());
    }

    public List<WalkListing> all() {
        return listings;
    }

    public Optional<WalkOffer> myOffer(UUID listingId) {
        return Optional.ofNullable(myOffers.get(listingId));
    }

    public int pendingOffers(UUID listingId) {
        return pendingOffers.getOrDefault(listingId, 0);
    }

    public Stage stage(WalkListing listing, Instant now) {
        boolean owner = listing.ownedBy(me);
        switch (listing.status()) {
            case COMPLETED:
                return owner || listing.walkedBy(me) ? Stage.COMPLETED : Stage.OFFER_DECLINED;
            case CANCELLED:
                return Stage.CANCELLED;
            case ASSIGNED:
                if (owner || listing.walkedBy(me)) {
                    return Stage.AGREED;
                }
                return Stage.OFFER_DECLINED;
            default:
                if (owner) {
                    if (!listing.acceptsOffers(now)) {
                        return Stage.EXPIRED;
                    }
                    return pendingOffers(listing.id()) > 0 ? Stage.OFFERS_TO_REVIEW : Stage.WAITING_FOR_OFFERS;
                }
                if (!listing.acceptsOffers(now) && myOffer(listing.id()).map(WalkOffer::pending).orElse(false)) {
                    return Stage.EXPIRED;
                }
                return offerStage(listing);
        }
    }

    private Stage offerStage(WalkListing listing) {
        WalkOffer offer = myOffers.get(listing.id());
        if (offer == null) {
            return Stage.CANCELLED;
        }
        return switch (offer.status()) {
            case PENDING -> Stage.OFFER_SENT;
            case ACCEPTED -> Stage.AGREED;
            case DECLINED -> Stage.OFFER_DECLINED;
            case WITHDRAWN -> Stage.OFFER_WITHDRAWN;
        };
    }

    /** Agreed walks first (soonest first), then finished ones. */
    public List<WalkListing> agreements(Instant now) {
        List<WalkListing> result = new ArrayList<>();
        for (WalkListing listing : listings) {
            Stage stage = stage(listing, now);
            if ((stage == Stage.AGREED || stage == Stage.COMPLETED) && (listing.ownedBy(me) || listing.walkedBy(me))) {
                result.add(listing);
            }
        }
        result.sort(Comparator.comparing((WalkListing listing) -> stage(listing, now) != Stage.AGREED)
                .thenComparing(listing -> listing.details().startsAt()));
        return result;
    }

    /** Listings I offered on and do not own, newest start first. */
    public List<WalkListing> sentOffers() {
        List<WalkListing> result = new ArrayList<>();
        for (WalkListing listing : listings) {
            if (!listing.ownedBy(me) && myOffers.containsKey(listing.id())) {
                result.add(listing);
            }
        }
        result.sort(Comparator.comparing((WalkListing listing) -> listing.details().startsAt()).reversed());
        return result;
    }

    /** My own requests, those that need an answer first. */
    public List<WalkListing> requests(Instant now) {
        List<WalkListing> result = new ArrayList<>();
        for (WalkListing listing : listings) {
            if (listing.ownedBy(me)) {
                result.add(listing);
            }
        }
        result.sort(Comparator.comparing((WalkListing listing) -> stage(listing, now) != Stage.OFFERS_TO_REVIEW)
                .thenComparing(Comparator.comparing((WalkListing listing) -> listing.details().startsAt()).reversed()));
        return result;
    }

    /** Things waiting for me: offers to answer on my requests and upcoming agreed walks. */
    public int attention(Instant now) {
        int count = 0;
        for (WalkListing listing : listings) {
            Stage stage = stage(listing, now);
            if (stage == Stage.OFFERS_TO_REVIEW || (stage == Stage.AGREED && listing.details().startsAt().isAfter(now.minusSeconds(3600)))) {
                count++;
            }
        }
        return count;
    }
}
