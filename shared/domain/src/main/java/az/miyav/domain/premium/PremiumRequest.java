package az.miyav.domain.premium;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A member's request to make a listing premium: how they paid, the uploaded receipt and the administrator's
 * decision. Listing and owner names are filled in for the review queue only.
 */
public final class PremiumRequest {
    public enum Method {
        M10("m10"), CARD("card");

        private final String databaseValue;

        Method(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static Method fromDatabase(String value) {
            for (Method method : values()) {
                if (method.databaseValue.equals(value)) {
                    return method;
                }
            }
            throw new IllegalArgumentException("Unknown payment method: " + value);
        }
    }

    public enum Status {
        PENDING("pending"), APPROVED("approved"), REJECTED("rejected");

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
            throw new IllegalArgumentException("Unknown premium status: " + value);
        }
    }

    private final UUID id;
    private final UUID listingId;
    private final String listingName;
    private final UUID ownerId;
    private final String ownerName;
    private final Method method;
    private final BigDecimal amount;
    private final int days;
    private final String receiptPath;
    private final Status status;
    private final String note;
    private final Instant createdAt;
    private final Instant reviewedAt;
    private final Instant premiumUntil;

    public PremiumRequest(UUID id, UUID listingId, String listingName, UUID ownerId, String ownerName, Method method,
                          BigDecimal amount, int days, String receiptPath, Status status, String note,
                          Instant createdAt, Instant reviewedAt, Instant premiumUntil) {
        this.id = Objects.requireNonNull(id, "id");
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.listingName = listingName == null ? "" : listingName;
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerName = ownerName == null ? "" : ownerName;
        this.method = Objects.requireNonNull(method, "method");
        this.amount = amount == null ? PremiumPlan.PRICE : amount;
        this.days = days;
        this.receiptPath = Objects.requireNonNull(receiptPath, "receiptPath");
        this.status = Objects.requireNonNull(status, "status");
        this.note = note == null ? "" : note;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
        this.premiumUntil = premiumUntil;
    }

    public UUID id() {
        return id;
    }

    public UUID listingId() {
        return listingId;
    }

    public String listingName() {
        return listingName;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public Method method() {
        return method;
    }

    public BigDecimal amount() {
        return amount;
    }

    public int days() {
        return days;
    }

    public String receiptPath() {
        return receiptPath;
    }

    public Status status() {
        return status;
    }

    public String note() {
        return note;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> reviewedAt() {
        return Optional.ofNullable(reviewedAt);
    }

    /** The listing's premium end at the time of reading; review queue only. */
    public Optional<Instant> premiumUntil() {
        return Optional.ofNullable(premiumUntil);
    }

    public boolean pending() {
        return status == Status.PENDING;
    }
}
