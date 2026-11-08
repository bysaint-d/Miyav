package az.miyav.domain.walk;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A pet owner asking a walker for help at a given time: who, which pet, when and where. Immutable;
 * the stored status and the time window together give the status people see.
 */
public final class WalkRequest {
    private final UUID id;
    private final Participant owner;
    private final Participant walker;
    private final String petName;
    private final String petType;
    private final CareService service;
    private final Instant startsAt;
    private final Instant endsAt;
    private final String storedStatus;
    private final BigDecimal totalPrice;
    private final String priceUnit;
    private final String city;
    private final String district;
    private final String notes;
    private final Instant createdAt;

    public WalkRequest(UUID id, Participant owner, Participant walker, String petName, String petType,
                       CareService service, Instant startsAt, Instant endsAt, String storedStatus,
                       BigDecimal totalPrice, String priceUnit, String city, String district,
                       String notes, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.walker = Objects.requireNonNull(walker, "walker");
        this.petName = petName == null ? "" : petName;
        this.petType = petType == null ? "" : petType;
        this.service = Objects.requireNonNull(service, "service");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
        if (endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("A walk cannot end before it starts");
        }
        WalkRequestStatus.fromDatabase(storedStatus);
        this.storedStatus = storedStatus;
        this.totalPrice = totalPrice == null ? BigDecimal.ZERO : totalPrice;
        this.priceUnit = priceUnit == null ? "" : priceUnit;
        this.city = city == null ? "" : city;
        this.district = district == null ? "" : district;
        this.notes = notes == null ? "" : notes;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public Participant owner() {
        return owner;
    }

    public Participant walker() {
        return walker;
    }

    public String petName() {
        return petName;
    }

    public String petType() {
        return petType;
    }

    public CareService service() {
        return service;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public Duration duration() {
        return Duration.between(startsAt, endsAt);
    }

    public WalkRequestStatus status(Instant now) {
        return WalkRequestStatus.resolve(storedStatus, startsAt, endsAt, now);
    }

    public BigDecimal totalPrice() {
        return totalPrice;
    }

    public String priceUnit() {
        return priceUnit;
    }

    /** The walker's public area. A request never carries a home address. */
    public String area() {
        if (district.isEmpty()) {
            return city;
        }
        return city.isEmpty() ? district : district + ", " + city;
    }

    public String notes() {
        return notes;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** True when the search text matches the owner, the walker or the pet. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        return owner.name().toLowerCase(java.util.Locale.ROOT).contains(needle)
                || walker.name().toLowerCase(java.util.Locale.ROOT).contains(needle)
                || petName.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    /** A person taking part in a request, identified by profile id and display name. */
    public static final class Participant {
        private final UUID id;
        private final String name;

        public Participant(UUID id, String name) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = name == null ? "" : name;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }
    }
}
