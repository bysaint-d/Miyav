package az.miyav.domain.walk;

import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.user.PhoneNumbers;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Another user who is walking nearby, with an approximate (~100 m) position and distance. */
public final class Walker {
    private final UUID sessionId;
    private final UUID userId;
    private final String fullName;
    private final String avatarPath;
    private final String bio;
    private final PetSpecies species;
    private final String petName;
    private final String message;
    private final double latitude;
    private final double longitude;
    private final int distanceMeters;
    private final Instant startedAt;
    private final String contactPhone;

    public Walker(UUID sessionId, UUID userId, String fullName, String avatarPath, String bio,
                  PetSpecies species, String petName, String message, double latitude, double longitude,
                  int distanceMeters, Instant startedAt, String contactPhone) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.fullName = fullName == null ? "" : fullName;
        this.avatarPath = avatarPath == null || avatarPath.isBlank() ? null : avatarPath;
        this.bio = bio == null ? "" : bio;
        this.species = Objects.requireNonNull(species, "species");
        this.petName = petName == null ? "" : petName;
        this.message = message == null ? "" : message;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = Math.max(0, distanceMeters);
        this.startedAt = startedAt;
        this.contactPhone = contactPhone;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID userId() {
        return userId;
    }

    public String fullName() {
        return fullName;
    }

    public String avatarPath() {
        return avatarPath;
    }

    public String bio() {
        return bio;
    }

    public PetSpecies species() {
        return species;
    }

    public String petName() {
        return petName;
    }

    public String message() {
        return message;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    public int distanceMeters() {
        return distanceMeters;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<String> contactDigits() {
        return PhoneNumbers.internationalDigits(contactPhone);
    }
}
