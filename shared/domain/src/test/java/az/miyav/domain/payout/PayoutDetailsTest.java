package az.miyav.domain.payout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutDetailsTest {
    private static final String VALID = "AZ21NABZ00000000137010001944";

    @Test
    void acceptsAzerbaijaniIbansWithRightCheckDigitsInAnySpacingOrCase() {
        assertTrue(PayoutDetails.validIban(VALID));
        assertTrue(PayoutDetails.validIban("az21 nabz 0000 0000 1370 1000 1944"));
        assertEquals(VALID, PayoutDetails.normalizeIban(" az21 NABZ 0000 0000 1370 1000 1944 "));
    }

    @Test
    void rejectsWrongCheckDigitsLengthOrCountry() {
        assertFalse(PayoutDetails.validIban("AZ22NABZ00000000137010001944"));
        assertFalse(PayoutDetails.validIban("AZ21NABZ0000000013701000194"));
        assertFalse(PayoutDetails.validIban("TR330006100519786457841326"));
        assertFalse(PayoutDetails.validIban(""));
        assertFalse(PayoutDetails.validIban(null));
    }

    @Test
    void groupsForDisplay() {
        assertEquals("AZ21 NABZ 0000 0000 1370 1000 1944", PayoutDetails.groupedIban(VALID));
    }
}
