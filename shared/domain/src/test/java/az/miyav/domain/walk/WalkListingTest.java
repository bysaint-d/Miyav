package az.miyav.domain.walk;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.PetSpecies;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkListingTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void theTotalFollowsTheHourlyPrice() {
        assertEquals(new BigDecimal("10.00"), details(60, "10").totalPrice());
        assertEquals(new BigDecimal("15.00"), details(90, "10").totalPrice());
        assertEquals(new BigDecimal("6.25"), details(30, "12.5").totalPrice());
    }

    @Test
    void invalidListingsCannotBeCreated() {
        assertEquals("validation.walk_price", assertThrows(ValidationException.class, () -> details(60, "0")).code());
        assertEquals("validation.walk_price", assertThrows(ValidationException.class, () -> details(60, "501")).code());
        assertEquals("validation.walk_duration", assertThrows(ValidationException.class, () -> details(10, "10")).code());
        assertEquals("validation.walk_pet", assertThrows(ValidationException.class, () -> new WalkListingDetails(" ",
                PetSpecies.DOG, NOW, 60, BigDecimal.TEN, "Bakı", null, "")).code());
        assertEquals("validation.city", assertThrows(ValidationException.class, () -> new WalkListingDetails("Boncuk",
                PetSpecies.DOG, NOW, 60, BigDecimal.TEN, "Gəncə", null, "")).code());
    }

    @Test
    void theAreaIsOnlyApproximate() {
        WalkListingDetails details = new WalkListingDetails("Boncuk", PetSpecies.DOG, NOW, 60, BigDecimal.TEN, "Bakı",
                new ListingLocation(40.41234, 49.86789, "Yasamal"), "");
        assertEquals(40.41, details.area().latitude());
        assertEquals(49.87, details.area().longitude());
    }

    @Test
    void walksMustBeInTheNearFuture() {
        details(60, "10").requireBookable(NOW.minusSeconds(3600));
        assertThrows(ValidationException.class, () -> details(60, "10").requireBookable(NOW.plusSeconds(3600)));
        assertThrows(ValidationException.class, () -> details(60, "10").requireBookable(NOW.minusSeconds(61L * 24 * 3600)));
    }

    @Test
    void offersAreOnlyPossibleWhileOpenAndUpcoming() {
        UUID owner = UUID.randomUUID();
        WalkListing open = new WalkListing(UUID.randomUUID(), owner, details(60, "10"), WalkListingStatus.OPEN, null, NOW);
        assertTrue(open.acceptsOffers(NOW.minusSeconds(60)));
        assertFalse(open.acceptsOffers(NOW.plusSeconds(60)));
        assertTrue(open.ownedBy(owner));
        UUID walker = UUID.randomUUID();
        WalkListing assigned = new WalkListing(open.id(), owner, open.details(), WalkListingStatus.ASSIGNED, walker, NOW);
        assertFalse(assigned.acceptsOffers(NOW.minusSeconds(60)));
        assertTrue(assigned.walkedBy(walker));
        assertTrue(assigned.completable(NOW.plusSeconds(60)));
        assertFalse(assigned.completable(NOW.minusSeconds(60)));
        assertThrows(IllegalArgumentException.class,
                () -> new WalkListing(open.id(), owner, open.details(), WalkListingStatus.OPEN, walker, NOW));
    }

    private static WalkListingDetails details(int minutes, String price) {
        return new WalkListingDetails("Boncuk", PetSpecies.DOG, NOW, minutes, new BigDecimal(price), "Bakı", null, "");
    }
}
