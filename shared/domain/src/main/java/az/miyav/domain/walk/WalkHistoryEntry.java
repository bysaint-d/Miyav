package az.miyav.domain.walk;

import az.miyav.domain.location.GeoPoint;
import az.miyav.domain.pet.PetSpecies;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One of the member's own walks: when, with whom, how far, and where it started and ended. */
public final class WalkHistoryEntry {
    private final UUID id;
    private final String petName;
    private final PetSpecies species;
    private final Instant startedAt;
    private final Instant endedAt;
    private final boolean open;
    private final double distanceMeters;
    private final int points;
    private final GeoPoint start;
    private final GeoPoint end;

    public WalkHistoryEntry(UUID id, String petName, PetSpecies species, Instant startedAt, Instant endedAt, boolean open,
                            double distanceMeters, int points, GeoPoint start, GeoPoint end) {
        this.id = Objects.requireNonNull(id, "id");
        this.petName = petName == null ? "" : petName;
        this.species = species;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.endedAt = endedAt;
        this.open = open;
        this.distanceMeters = Math.max(0, distanceMeters);
        this.points = Math.max(0, points);
        this.start = start;
        this.end = end;
    }

    public UUID id() {
        return id;
    }

    public String petName() {
        return petName;
    }

    /** Null when the walk was started before species were recorded. */
    public PetSpecies species() {
        return species;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** Empty while the walk is still going. */
    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    public boolean open() {
        return open;
    }

    public double distanceMeters() {
        return distanceMeters;
    }

    /** A route needs at least two recorded points. */
    public boolean hasRoute() {
        return points >= 2;
    }

    public Optional<GeoPoint> start() {
        return Optional.ofNullable(start);
    }

    public Optional<GeoPoint> end() {
        return Optional.ofNullable(end);
    }

    public Duration duration(Instant now) {
        Instant until = endedAt == null ? now : endedAt;
        return until.isBefore(startedAt) ? Duration.ZERO : Duration.between(startedAt, until);
    }
}
