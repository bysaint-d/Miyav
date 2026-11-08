package az.miyav.domain.walk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * "I need someone to walk my pet": an owner's public walking listing. Walkers send {@link WalkOffer}s;
 * the owner accepts one, which assigns that walker. Composed of the owner's {@link WalkListingDetails}.
 */
public final class WalkListing {
    private final UUID id;
    private final UUID ownerId;
    private final WalkListingDetails details;
    private final WalkListingStatus status;
    private final UUID walkerId;
    private final BigDecimal agreedPricePerHour;
    private final Instant createdAt;

    public WalkListing(UUID id, UUID ownerId, WalkListingDetails details, WalkListingStatus status, UUID walkerId,
                       Instant createdAt) {
        this(id, ownerId, details, status, walkerId, null, createdAt);
    }

    /** {@code agreedPricePerHour} is fixed when the owner accepts an offer; null while open. */
    public WalkListing(UUID id, UUID ownerId, WalkListingDetails details, WalkListingStatus status, UUID walkerId,
                       BigDecimal agreedPricePerHour, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.details = Objects.requireNonNull(details, "details");
        this.status = Objects.requireNonNull(status, "status");
        if (status == WalkListingStatus.OPEN && walkerId != null) {
            throw new IllegalArgumentException("An open listing has no walker yet");
        }
        this.walkerId = walkerId;
        this.agreedPricePerHour = agreedPricePerHour == null ? null : agreedPricePerHour.setScale(2, RoundingMode.HALF_UP);
        this.createdAt = createdAt;
    }

    public Optional<BigDecimal> agreedPricePerHour() {
        return Optional.ofNullable(agreedPricePerHour);
    }

    /** The agreed hourly price once a walker is chosen, otherwise the price the owner asked. */
    public BigDecimal effectivePricePerHour() {
        return agreedPricePerHour == null ? details.pricePerHour() : agreedPricePerHour;
    }

    public BigDecimal effectiveTotal() {
        return WalkListingDetails.total(effectivePricePerHour(), details.durationMinutes());
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public WalkListingDetails details() {
        return details;
    }

    public WalkListingStatus status() {
        return status;
    }

    public Optional<UUID> walkerId() {
        return Optional.ofNullable(walkerId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean ownedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public boolean walkedBy(UUID userId) {
        return walkerId != null && walkerId.equals(userId);
    }

    /** Open and not started yet: walkers can still offer. */
    public boolean acceptsOffers(Instant now) {
        return status == WalkListingStatus.OPEN && details.startsAt().isAfter(now);
    }

    /** An assigned walk can be marked done once it has started. */
    public boolean completable(Instant now) {
        return status == WalkListingStatus.ASSIGNED && !details.startsAt().isAfter(now);
    }
}
