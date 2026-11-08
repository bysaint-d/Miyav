package az.miyav.domain.walk;

import az.miyav.domain.pet.PetSpecies;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A pet out on a walk, as shown on the map card: one of the walker's own pets, or the pet of a walk job
 * they were chosen for ({@link #fromJob()}).
 */
public final class WalkPet {
    private final String name;
    private final PetSpecies species;
    private final String breed;
    private final BigDecimal ageYears;
    private final String photoPath;
    private final boolean fromJob;

    public WalkPet(String name, PetSpecies species, String breed, BigDecimal ageYears, String photoPath, boolean fromJob) {
        this.name = name == null ? "" : name.trim();
        this.species = Objects.requireNonNull(species, "species");
        this.breed = breed == null ? "" : breed.trim();
        this.ageYears = ageYears;
        this.photoPath = photoPath == null || photoPath.isBlank() ? null : photoPath;
        this.fromJob = fromJob;
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

    public Optional<BigDecimal> ageYears() {
        return Optional.ofNullable(ageYears);
    }

    public Optional<String> photoPath() {
        return Optional.ofNullable(photoPath);
    }

    public boolean fromJob() {
        return fromJob;
    }
}
