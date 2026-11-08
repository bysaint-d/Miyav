package az.miyav.domain.walk;

import java.time.Instant;

/**
 * Lifecycle of a walking request (a row in {@code bookings}). The database stores five values;
 * {@link #IN_PROGRESS} is derived: an accepted request whose time window is happening now.
 */
public enum WalkRequestStatus {
    PENDING("pending"),
    ACCEPTED("accepted"),
    IN_PROGRESS("accepted"),
    COMPLETED("completed"),
    DECLINED("declined"),
    CANCELLED("cancelled");

    private final String databaseValue;

    WalkRequestStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    /** Status as people experience it at {@code now}. */
    public static WalkRequestStatus resolve(String databaseValue, Instant startsAt, Instant endsAt, Instant now) {
        WalkRequestStatus stored = fromDatabase(databaseValue);
        if (stored == ACCEPTED && startsAt != null && endsAt != null
                && !now.isBefore(startsAt) && now.isBefore(endsAt)) {
            return IN_PROGRESS;
        }
        return stored;
    }

    public static WalkRequestStatus fromDatabase(String value) {
        for (WalkRequestStatus status : values()) {
            if (status != IN_PROGRESS && status.databaseValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown walking request status: " + value);
    }

    /** Still open to change: waiting for the walker, agreed, or under way. */
    public boolean open() {
        return this == PENDING || this == ACCEPTED || this == IN_PROGRESS;
    }

    /** Moderators may cancel a request that has not finished. */
    public boolean cancellableByModerator() {
        return open();
    }
}
