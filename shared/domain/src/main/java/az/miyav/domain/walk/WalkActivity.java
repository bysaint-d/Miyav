package az.miyav.domain.walk;

import az.miyav.domain.location.GeoPoint;
import az.miyav.domain.pet.PetSpecies;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A NearMe walk as moderators see it: who walked which pet and when. A position exists only while the
 * walk is live, and only rounded to about 1 km.
 */
public final class WalkActivity {
    private final UUID id;
    private final UUID userId;
    private final String userName;
    private final String petName;
    private final PetSpecies species;
    private final WalkSession.Status status;
    private final String message;
    private final Instant startedAt;
    private final Instant endedAt;
    private final GeoPoint approximatePosition;

    public WalkActivity(UUID id, UUID userId, String userName, String petName, PetSpecies species,
                        WalkSession.Status status, String message, Instant startedAt, Instant endedAt,
                        GeoPoint approximatePosition) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.userName = userName == null ? "" : userName;
        this.petName = petName == null ? "" : petName;
        this.species = Objects.requireNonNull(species, "species");
        this.status = Objects.requireNonNull(status, "status");
        this.message = message == null ? "" : message;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.endedAt = endedAt;
        this.approximatePosition = status == WalkSession.Status.ACTIVE ? approximatePosition : null;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String userName() {
        return userName;
    }

    public String petName() {
        return petName;
    }

    public PetSpecies species() {
        return species;
    }

    public WalkSession.Status status() {
        return status;
    }

    public String message() {
        return message;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    public boolean live() {
        return status == WalkSession.Status.ACTIVE;
    }

    public Optional<GeoPoint> approximatePosition() {
        return Optional.ofNullable(approximatePosition);
    }
}
