package az.miyav.domain.pet;

import az.miyav.common.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * What an owner types for one of their own pets ("Mənim heyvanlarım"). Validated on construction with the
 * same limits as the {@code pets} table, so a draft that exists can always be saved.
 */
public final class PetProfileDraft {
    public static final BigDecimal MAX_AGE = new BigDecimal("80");
    public static final BigDecimal MAX_WEIGHT = new BigDecimal("300");

    private final String name;
    private final PetSpecies species;
    private final String breed;
    private final BigDecimal ageYears;
    private final BigDecimal weightKg;
    private final String careNotes;

    public PetProfileDraft(String name, PetSpecies species, String breed, BigDecimal ageYears, BigDecimal weightKg,
                           String careNotes) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 60) {
            throw new ValidationException("petprofile.name");
        }
        String breedText = breed == null ? "" : breed.trim();
        if (breedText.length() > 80) {
            throw new ValidationException("petprofile.breed");
        }
        if (ageYears == null || ageYears.signum() < 0 || ageYears.compareTo(MAX_AGE) > 0) {
            throw new ValidationException("petprofile.age");
        }
        if (weightKg == null || weightKg.signum() <= 0 || weightKg.compareTo(MAX_WEIGHT) > 0) {
            throw new ValidationException("petprofile.weight");
        }
        String notes = careNotes == null ? "" : careNotes.trim();
        if (notes.length() > 2000) {
            throw new ValidationException("petprofile.notes");
        }
        this.name = trimmed;
        this.species = Objects.requireNonNull(species, "species");
        this.breed = breedText;
        this.ageYears = ageYears.setScale(1, RoundingMode.HALF_UP);
        this.weightKg = weightKg.setScale(1, RoundingMode.HALF_UP);
        this.careNotes = notes;
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

    public String careNotes() {
        return careNotes;
    }
}
