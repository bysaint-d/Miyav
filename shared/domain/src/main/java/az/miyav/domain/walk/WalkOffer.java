package az.miyav.domain.walk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A walker's answer to a walking listing: "I can walk your pet", with their hourly price and a short message. */
public final class WalkOffer {
    public enum Status {
        PENDING("pending"), ACCEPTED("accepted"), DECLINED("declined"), WITHDRAWN("withdrawn");

        private final String databaseValue;

        Status(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static Status fromDatabase(String value) {
            for (Status status : values()) {
                if (status.databaseValue.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown walk offer status: " + value);
        }
    }

    private final UUID id;
    private final UUID listingId;
    private final UUID walkerId;
    private final String message;
    private final Status status;
    private final BigDecimal pricePerHour;
    private final Instant createdAt;

    public WalkOffer(UUID id, UUID listingId, UUID walkerId, String message, Status status, Instant createdAt) {
        this(id, listingId, walkerId, message, status, null, createdAt);
    }

    /** {@code pricePerHour} is the walker's own hourly price; null means the listing's price. */
    public WalkOffer(UUID id, UUID listingId, UUID walkerId, String message, Status status, BigDecimal pricePerHour,
                     Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.walkerId = Objects.requireNonNull(walkerId, "walkerId");
        this.message = message == null ? "" : message;
        this.status = Objects.requireNonNull(status, "status");
        this.pricePerHour = pricePerHour == null ? null : pricePerHour.setScale(2, RoundingMode.HALF_UP);
        this.createdAt = createdAt;
    }

    public Optional<BigDecimal> pricePerHour() {
        return Optional.ofNullable(pricePerHour);
    }

    /** The hourly price this walker asks for this listing. */
    public BigDecimal priceFor(WalkListingDetails details) {
        return pricePerHour == null ? details.pricePerHour() : pricePerHour;
    }

    /** True when the walker asks for a different price than the owner offered. */
    public boolean counterOffer(WalkListingDetails details) {
        return pricePerHour != null && pricePerHour.compareTo(details.pricePerHour()) != 0;
    }

    public UUID id() {
        return id;
    }

    public UUID listingId() {
        return listingId;
    }

    public UUID walkerId() {
        return walkerId;
    }

    public String message() {
        return message;
    }

    public Status status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean pending() {
        return status == Status.PENDING;
    }
}
