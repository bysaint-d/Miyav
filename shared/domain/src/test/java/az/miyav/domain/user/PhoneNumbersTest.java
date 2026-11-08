package az.miyav.domain.user;

import az.miyav.domain.location.ListingLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumbersTest {
    @Test
    void azerbaijaniFormatsBecomeInternationalDigits() {
        assertEquals(Optional.of("994501234567"), PhoneNumbers.internationalDigits("+994 50 123 45 67"));
        assertEquals(Optional.of("994501234567"), PhoneNumbers.internationalDigits("050-123-45-67"));
        assertEquals(Optional.of("994501234567"), PhoneNumbers.internationalDigits("50 123 45 67"));
        assertEquals(Optional.of("994501234567"), PhoneNumbers.internationalDigits("00994501234567"));
        assertEquals(Optional.of("905321234567"), PhoneNumbers.internationalDigits("+90 532 123 45 67"));
    }

    @Test
    void numbersThatCannotBeNormalisedAreNotGuessed() {
        assertFalse(PhoneNumbers.internationalDigits("").isPresent());
        assertFalse(PhoneNumbers.internationalDigits(null).isPresent());
        assertFalse(PhoneNumbers.internationalDigits("+994 50 123").isPresent());
        assertFalse(PhoneNumbers.internationalDigits("call me").isPresent());
        assertFalse(PhoneNumbers.internationalDigits("12345").isPresent());
        assertTrue(PhoneNumbers.valid(""));
        assertFalse(PhoneNumbers.valid("abc"));
    }

    @Test
    void listingLocationIsRoundedToAboutOneHundredMetresAndBoundedToTheServiceArea() {
        ListingLocation location = new ListingLocation(40.409312, 49.867154, "Nəsimi, Bakı");
        assertEquals(40.409, location.latitude());
        assertEquals(49.867, location.longitude());
        assertThrows(RuntimeException.class, () -> new ListingLocation(39.23, -123.15, ""));
        double distance = ListingLocation.distanceMeters(40.4093, 49.8671, 40.5897, 49.6686);
        assertTrue(distance > 25_000 && distance < 27_000);
    }
}
