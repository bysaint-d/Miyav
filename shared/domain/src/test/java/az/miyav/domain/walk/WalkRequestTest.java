package az.miyav.domain.walk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkRequestTest {
    private static final Instant START = Instant.parse("2026-11-08T10:00:00Z");
    private static final Instant END = Instant.parse("2026-11-08T11:00:00Z");

    @Test
    void anAcceptedWalkIsInProgressDuringItsTimeWindow() {
        WalkRequest walk = request("accepted");
        assertEquals(WalkRequestStatus.ACCEPTED, walk.status(START.minusSeconds(60)));
        assertEquals(WalkRequestStatus.IN_PROGRESS, walk.status(START.plusSeconds(600)));
        assertEquals(WalkRequestStatus.ACCEPTED, walk.status(END.plusSeconds(60)));
        assertEquals(60, walk.duration().toMinutes());
    }

    @Test
    void onlyUnfinishedRequestsCanBeCancelledByAModerator() {
        assertTrue(WalkRequestStatus.PENDING.cancellableByModerator());
        assertTrue(WalkRequestStatus.IN_PROGRESS.cancellableByModerator());
        assertFalse(WalkRequestStatus.COMPLETED.cancellableByModerator());
        assertFalse(WalkRequestStatus.DECLINED.cancellableByModerator());
        assertEquals("accepted", WalkRequestStatus.IN_PROGRESS.databaseValue());
    }

    @Test
    void invalidRequestsCannotBeCreated() {
        assertThrows(IllegalArgumentException.class, () -> request("open"));
        assertThrows(IllegalArgumentException.class, () -> new WalkRequest(UUID.randomUUID(), participant("Aysel"),
                participant("Murad"), "Boncuk", "dog", CareService.WALKING, END, START, "pending",
                BigDecimal.TEN, "hour", "Bakı", "Yasamal", "", START));
    }

    @Test
    void searchMatchesPeopleAndPetsAndTheAreaHidesAddresses() {
        WalkRequest walk = request("pending");
        assertTrue(walk.matches("ays"));
        assertTrue(walk.matches("MURAD"));
        assertTrue(walk.matches("bonc"));
        assertFalse(walk.matches("Sumqayıt"));
        assertEquals("Yasamal, Bakı", walk.area());
    }

    private static WalkRequest request(String status) {
        return new WalkRequest(UUID.randomUUID(), participant("Aysel Məmmədova"), participant("Murad"), "Boncuk", "dog",
                CareService.WALKING, START, END, status, new BigDecimal("12"), "hour", "Bakı", "Yasamal", "", START);
    }

    private static WalkRequest.Participant participant(String name) {
        return new WalkRequest.Participant(UUID.randomUUID(), name);
    }
}
