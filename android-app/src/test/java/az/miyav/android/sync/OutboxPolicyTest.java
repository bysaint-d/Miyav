package az.miyav.android.sync;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OutboxPolicyTest {
    @Test
    public void transientFailuresRetryUntilTheLimit() {
        assertEquals(OutboxPolicy.Decision.RETRY, OutboxPolicy.afterFailure("network.unavailable", 0));
        assertEquals(OutboxPolicy.Decision.RETRY, OutboxPolicy.afterFailure("auth.session_expired", 3));
        assertEquals(OutboxPolicy.Decision.DROP, OutboxPolicy.afterFailure("network.unavailable", OutboxPolicy.MAX_ATTEMPTS - 1));
    }

    @Test
    public void businessRejectionsAreDroppedSoTheQueueStaysIdempotent() {
        assertEquals(OutboxPolicy.Decision.DROP, OutboxPolicy.afterFailure("adoption.duplicate", 0));
        assertEquals(OutboxPolicy.Decision.DROP, OutboxPolicy.afterFailure("adoption.listing_unavailable", 0));
        assertEquals(OutboxPolicy.Decision.DROP, OutboxPolicy.afterFailure("access.owner", 0));
    }

    @Test
    public void adoptionPayloadRoundTripsIncludingPipesInTheMessage() {
        UUID listing = UUID.randomUUID();
        OutboxPolicy.Adoption decoded = OutboxPolicy.decodeAdoption(
                OutboxPolicy.encodeAdoption(listing, "Salam | mən hazıram"));
        assertEquals(listing, decoded.listingId);
        assertEquals("Salam | mən hazıram", decoded.message);
        assertNull(OutboxPolicy.decodeAdoption("not-a-uuid|text"));
        assertNull(OutboxPolicy.decodeAdoption(null));
    }
}
