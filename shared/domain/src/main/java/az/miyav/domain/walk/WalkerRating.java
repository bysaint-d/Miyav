package az.miyav.domain.walk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/** A walker's average stars from owners and how many walks were rated. */
public final class WalkerRating {
    private final UUID walkerId;
    private final BigDecimal average;
    private final int count;

    public WalkerRating(UUID walkerId, BigDecimal average, int count) {
        this.walkerId = Objects.requireNonNull(walkerId, "walkerId");
        this.average = Objects.requireNonNull(average, "average").setScale(1, RoundingMode.HALF_UP);
        this.count = count;
    }

    public UUID walkerId() {
        return walkerId;
    }

    public BigDecimal average() {
        return average;
    }

    public int count() {
        return count;
    }
}
