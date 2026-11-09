package az.miyav.domain.walk;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Totals for the walk history header: this week, all time, the longest walk and the daily streak. */
public final class WalkStats {
    private final int weekWalks;
    private final double weekMeters;
    private final Duration weekTime;
    private final int totalWalks;
    private final double totalMeters;
    private final double longestMeters;
    private final int streakDays;

    private WalkStats(int weekWalks, double weekMeters, Duration weekTime, int totalWalks, double totalMeters,
                      double longestMeters, int streakDays) {
        this.weekWalks = weekWalks;
        this.weekMeters = weekMeters;
        this.weekTime = weekTime;
        this.totalWalks = totalWalks;
        this.totalMeters = totalMeters;
        this.longestMeters = longestMeters;
        this.streakDays = streakDays;
    }

    /** Weeks start on Monday in {@code zone}; the streak counts days in a row up to today or yesterday. */
    public static WalkStats of(List<WalkHistoryEntry> walks, Instant now, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekWalks = 0;
        double weekMeters = 0;
        Duration weekTime = Duration.ZERO;
        double totalMeters = 0;
        double longest = 0;
        Set<LocalDate> days = new TreeSet<>();
        for (WalkHistoryEntry walk : walks) {
            LocalDate day = LocalDate.ofInstant(walk.startedAt(), zone);
            days.add(day);
            totalMeters += walk.distanceMeters();
            longest = Math.max(longest, walk.distanceMeters());
            if (!day.isBefore(monday)) {
                weekWalks++;
                weekMeters += walk.distanceMeters();
                weekTime = weekTime.plus(walk.duration(now));
            }
        }
        int streak = 0;
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return new WalkStats(weekWalks, weekMeters, weekTime, walks.size(), totalMeters, longest, streak);
    }

    public int weekWalks() {
        return weekWalks;
    }

    public double weekMeters() {
        return weekMeters;
    }

    public Duration weekTime() {
        return weekTime;
    }

    public int totalWalks() {
        return totalWalks;
    }

    public double totalMeters() {
        return totalMeters;
    }

    public double longestMeters() {
        return longestMeters;
    }

    public int streakDays() {
        return streakDays;
    }
}
