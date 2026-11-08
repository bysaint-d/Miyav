package az.miyav.domain.admin;

import java.time.LocalDate;
import java.util.Objects;

/** One point of a daily chart, in Baku time. */
public final class DailyCount {
    private final LocalDate day;
    private final int count;

    public DailyCount(LocalDate day, int count) {
        this.day = Objects.requireNonNull(day, "day");
        if (count < 0) {
            throw new IllegalArgumentException("count");
        }
        this.count = count;
    }

    public LocalDate day() {
        return day;
    }

    public int count() {
        return count;
    }
}
