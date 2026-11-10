package az.miyav.domain.walk;

import az.miyav.domain.pet.PetSpecies;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MyWalksTest {
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void ownersSeeWhetherOffersWaitForThem() {
        WalkListing quiet = listing(me, WalkListingStatus.OPEN, null);
        WalkListing busy = listing(me, WalkListingStatus.OPEN, null);
        MyWalks walks = new MyWalks(me, List.of(quiet, busy), Map.of(), Map.of(busy.id(), 2));

        assertEquals(MyWalks.Stage.WAITING_FOR_OFFERS, walks.stage(quiet, NOW));
        assertEquals(MyWalks.Stage.OFFERS_TO_REVIEW, walks.stage(busy, NOW));
        assertEquals(busy, walks.requests(NOW).get(0));
        assertEquals(1, walks.attention(NOW));
    }

    @Test
    void walkersSeeTheOwnersAnswer() {
        WalkListing waiting = listing(other, WalkListingStatus.OPEN, null);
        WalkListing chosen = listing(other, WalkListingStatus.ASSIGNED, me);
        WalkListing someoneElse = listing(other, WalkListingStatus.ASSIGNED, UUID.randomUUID());
        MyWalks walks = new MyWalks(me, List.of(waiting, chosen, someoneElse), Map.of(
                waiting.id(), offer(waiting, WalkOffer.Status.PENDING),
                chosen.id(), offer(chosen, WalkOffer.Status.ACCEPTED),
                someoneElse.id(), offer(someoneElse, WalkOffer.Status.DECLINED)), Map.of());

        assertEquals(MyWalks.Stage.OFFER_SENT, walks.stage(waiting, NOW));
        assertEquals(MyWalks.Stage.AGREED, walks.stage(chosen, NOW));
        assertEquals(MyWalks.Stage.OFFER_DECLINED, walks.stage(someoneElse, NOW));
        assertEquals(List.of(chosen), walks.agreements(NOW));
        assertEquals(3, walks.sentOffers().size());
    }

    @Test
    void unansweredOffersExpireWithTheStartTime() {
        WalkListing past = listing(other, WalkListingStatus.OPEN, null, NOW.minusSeconds(600));
        MyWalks walks = new MyWalks(me, List.of(past), Map.of(past.id(), offer(past, WalkOffer.Status.PENDING)), Map.of());

        assertEquals(MyWalks.Stage.EXPIRED, walks.stage(past, NOW));
    }

    private WalkListing listing(UUID owner, WalkListingStatus status, UUID walker) {
        return listing(owner, status, walker, NOW.plusSeconds(3600));
    }

    private WalkListing listing(UUID owner, WalkListingStatus status, UUID walker, Instant startsAt) {
        WalkListingDetails details = new WalkListingDetails("Boncuk", PetSpecies.DOG, startsAt, 60, BigDecimal.TEN, "Bakı", null, "");
        return new WalkListing(UUID.randomUUID(), owner, details, status, walker,
                walker == null ? null : BigDecimal.TEN, NOW.minusSeconds(7200));
    }

    private WalkOffer offer(WalkListing listing, WalkOffer.Status status) {
        return new WalkOffer(UUID.randomUUID(), listing.id(), me, "", status, BigDecimal.TEN, NOW.minusSeconds(60));
    }
}
