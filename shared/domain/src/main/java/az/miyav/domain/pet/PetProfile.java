package az.miyav.domain.pet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A member's own pet (table {@code pets}), the animal that walking requests are about. Separate from
 * {@link Pet}, which models an adoption listing. Care notes and emergency contacts are not part of it.
 */
public final class PetProfile {
    private final UUID id;
    private final UUID ownerId;
    private final String ownerName;
    private final String name;
    private final PetSpecies species;
    private final String breed;
    private final BigDecimal ageYears;
    private final BigDecimal weightKg;
    private final String photoPath;
    private final String careNotes;
    private final Instant createdAt;

    public PetProfile(UUID id, UUID ownerId, String ownerName, String name, PetSpecies species, String breed,
                      BigDecimal ageYears, BigDecimal weightKg, String photoPath, Instant createdAt) {
        this(id, ownerId, ownerName, name, species, breed, ageYears, weightKg, photoPath, "", createdAt);
    }

    /** {@code careNotes} are only ever loaded for the owner. */
    public PetProfile(UUID id, UUID ownerId, String ownerName, String name, PetSpecies species, String breed,
                      BigDecimal ageYears, BigDecimal weightKg, String photoPath, String careNotes, Instant createdAt) {
        this.careNotes = careNotes == null ? "" : careNotes;
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerName = ownerName == null ? "" : ownerName;
        this.name = name == null ? "" : name;
        this.species = Objects.requireNonNull(species, "species");
        this.breed = breed == null ? "" : breed;
        this.ageYears = ageYears;
        this.weightKg = weightKg;
        this.photoPath = photoPath == null || photoPath.isBlank() ? null : photoPath;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public String name() {
        return name;
    }

    public PetSpecies species() {
        return species;
    }

    public String breed() {
        return breed;
    }

    public BigDecimal ageYears() {
        return ageYears;
    }

    public BigDecimal weightKg() {
        return weightKg;
    }

    public String photoPath() {
        return photoPath;
    }

    public String careNotes() {
        return careNotes;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
