package az.miyav.application;

import az.miyav.domain.walk.WalkHistoryEntry;
import az.miyav.domain.walk.WalkStats;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalkStatsTest {
    // Tuesday 10 Nov 2026, 12:00 in Baku.
    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    @Test
    void weekTotalsLongestAndStreak() {
        List<WalkHistoryEntry> walks = List.of(
                walk(NOW.minus(2, ChronoUnit.HOURS), 30, 1200),
                walk(NOW.minus(1, ChronoUnit.DAYS), 45, 2500),
                walk(NOW.minus(2, ChronoUnit.DAYS), 20, 800),
                walk(NOW.minus(9, ChronoUnit.DAYS), 60, 4000));
        WalkStats stats = WalkingService.stats(walks, NOW);
        assertEquals(2, stats.weekWalks(), "Monday and Tuesday walks only");
        assertEquals(3700, stats.weekMeters(), 0.1);
        assertEquals(75, stats.weekTime().toMinutes());
        assertEquals(4, stats.totalWalks());
        assertEquals(8500, stats.totalMeters(), 0.1);
        assertEquals(4000, stats.longestMeters(), 0.1);
        assertEquals(3, stats.streakDays());
    }

    @Test
    void streakStillCountsWhenTodaysWalkIsMissing() {
        WalkStats stats = WalkingService.stats(List.of(walk(NOW.minus(1, ChronoUnit.DAYS), 10, 500)), NOW);
        assertEquals(1, stats.streakDays());
        assertEquals(0, WalkingService.stats(List.of(walk(NOW.minus(3, ChronoUnit.DAYS), 10, 500)), NOW).streakDays());
    }

    private static WalkHistoryEntry walk(Instant start, int minutes, double meters) {
        return new WalkHistoryEntry(UUID.randomUUID(), "Boncuk", null, start, start.plus(minutes, ChronoUnit.MINUTES), false,
                meters, 10, null, null);
    }
}
