package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.WalkListingRepository;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkListingStatus;
import az.miyav.domain.walk.WalkOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WalkListingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private final WalkListingRepository repository = mock(WalkListingRepository.class);
    private final WalkListingService service = new WalkListingService(repository, () -> NOW);
    private final AuthSession owner = session();
    private final AuthSession walker = session();

    @Test
    void guestsCannotPostOrOffer() {
        WalkListing listing = listing(WalkListingStatus.OPEN, null);
        assertThrows(AccessDeniedException.class, () -> service.publish(null, details(NOW.plusSeconds(3600))));
        assertThrows(AccessDeniedException.class, () -> service.offer(null, listing, null, "Salam"));
        verifyNoInteractions(repository);
    }

    @Test
    void ownersPublishOnlyUpcomingWalks() {
        service.publish(owner, details(NOW.plusSeconds(3600)));
        verify(repository).create(any(), any());
        ValidationException past = assertThrows(ValidationException.class, () -> service.publish(owner, details(NOW.minusSeconds(3600))));
        assertEquals("validation.walk_time", past.code());
    }

    @Test
    void ownersCannotOfferOnTheirOwnListingAndClosedListingsTakeNoOffers() {
        WalkListing open = listing(WalkListingStatus.OPEN, null);
        assertEquals("walk.own_listing", assertThrows(ValidationException.class, () -> service.offer(owner, open, null, "")).code());
        service.offer(walker, open, null, "Hər gün parkda gəzirəm.");
        verify(repository).offer(walker, open.id(), new BigDecimal("10.00"), "Hər gün parkda gəzirəm.");

        WalkListing assigned = listing(WalkListingStatus.ASSIGNED, UUID.randomUUID());
        assertEquals("walk.listing_closed", assertThrows(ValidationException.class, () -> service.offer(walker, assigned, null, "")).code());
    }

    @Test
    void walkersMayAskForTheirOwnPriceWithinLimits() {
        WalkListing open = listing(WalkListingStatus.OPEN, null);
        service.offer(walker, open, new BigDecimal("20"), "Peşəkaram");
        verify(repository).offer(walker, open.id(), new BigDecimal("20.00"), "Peşəkaram");
        assertEquals("validation.walk_price",
                assertThrows(ValidationException.class, () -> service.offer(walker, open, new BigDecimal("0.5"), "")).code());
        assertEquals("validation.walk_price",
                assertThrows(ValidationException.class, () -> service.offer(walker, open, new BigDecimal("900"), "")).code());
    }

    @Test
    void theAgreedPriceReplacesTheAskedPrice() {
        WalkListing assigned = new WalkListing(UUID.randomUUID(), owner.userId(), details(NOW.plusSeconds(3600)),
                WalkListingStatus.ASSIGNED, walker.userId(), new BigDecimal("20"), NOW);
        assertEquals(new BigDecimal("20.00"), assigned.effectivePricePerHour());
        assertEquals(new BigDecimal("20.00"), assigned.effectiveTotal());
        WalkOffer counter = new WalkOffer(UUID.randomUUID(), assigned.id(), walker.userId(), "", WalkOffer.Status.PENDING,
                new BigDecimal("20"), NOW);
        assertEquals(true, counter.counterOffer(assigned.details()));
    }

    @Test
    void onlyTheOwnerChoosesAWalker() {
        WalkListing open = listing(WalkListingStatus.OPEN, null);
        WalkOffer offer = new WalkOffer(UUID.randomUUID(), open.id(), walker.userId(), "", WalkOffer.Status.PENDING, NOW);

        assertThrows(AccessDeniedException.class, () -> service.respond(walker, open, offer, true));
        service.respond(owner, open, offer, true);
        verify(repository).respond(owner, offer.id(), true);

        WalkOffer declined = new WalkOffer(UUID.randomUUID(), open.id(), walker.userId(), "", WalkOffer.Status.DECLINED, NOW);
        assertEquals("walk.offer_closed", assertThrows(ValidationException.class, () -> service.respond(owner, open, declined, true)).code());
        verify(repository, never()).respond(owner, declined.id(), true);
    }

    @Test
    void walkersWithdrawOnlyTheirOwnPendingOffer() {
        WalkOffer mine = new WalkOffer(UUID.randomUUID(), UUID.randomUUID(), walker.userId(), "", WalkOffer.Status.PENDING, NOW);
        assertThrows(AccessDeniedException.class, () -> service.withdraw(owner, mine));
        service.withdraw(walker, mine);
        verify(repository).withdraw(walker, mine.id());
        verify(repository, never()).respond(any(), any(), anyBoolean());
    }

    private WalkListing listing(WalkListingStatus status, UUID walkerId) {
        return new WalkListing(UUID.randomUUID(), owner.userId(), details(NOW.plusSeconds(3600)), status, walkerId, NOW);
    }

    private static WalkListingDetails details(Instant startsAt) {
        return new WalkListingDetails("Boncuk", PetSpecies.DOG, startsAt, 60, new BigDecimal("10"), "Bakı", null, "");
    }

    private static AuthSession session() {
        return new AuthSession(UUID.randomUUID(), "a@example.com", "t", "r", Instant.MAX, UserRole.OWNER);
    }
}
