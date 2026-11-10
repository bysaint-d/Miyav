package az.miyav.domain.pet;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.text.TextRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public abstract class Pet {
    public static final Set<String> SUPPORTED_CITIES = Set.of("Bakı", "Sumqayıt", "Abşeron");

    private final UUID id;
    private final UUID ownerId;
    private final String name;
    private final PetSpecies species;
    private final String breed;
    private final int ageMonths;
    private final PetGender gender;
    private final String city;
    private final String description;
    private final PetStatus status;
    private final List<String> photoPaths;
    private ListingExtras extras = ListingExtras.NONE;

    protected Pet(
            UUID id,
            UUID ownerId,
            String name,
            PetSpecies species,
            String breed,
            int ageMonths,
            PetGender gender,
            String city,
            String description,
            PetStatus status,
            List<String> photoPaths
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.name = TextRules.required(name, 1, 80, "validation.pet");
        this.species = Objects.requireNonNull(species, "species");
        this.breed = TextRules.optional(breed, 100, "validation.pet");
        if (ageMonths < 0 || ageMonths > 1200) {
            throw new ValidationException("validation.pet");
        }
        this.ageMonths = ageMonths;
        this.gender = gender == null ? PetGender.UNKNOWN : gender;
        this.city = TextRules.required(city, 1, 40, "validation.city");
        if (!SUPPORTED_CITIES.contains(this.city)) {
            throw new ValidationException("validation.city");
        }
        this.description = TextRules.required(description, 10, 4000, "validation.pet");
        this.status = Objects.requireNonNull(status, "status");
        if (photoPaths == null || photoPaths.size() > 5) {
            throw new ValidationException("validation.pet");
        }
        if (status == PetStatus.PUBLISHED && photoPaths.isEmpty()) {
            throw new ValidationException("validation.pet");
        }
        this.photoPaths = Collections.unmodifiableList(new ArrayList<>(photoPaths));
    }

    public abstract CareProfile defaultCareProfile();

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
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

    public int ageMonths() {
        return ageMonths;
    }

    public PetGender gender() {
        return gender;
    }

    public String city() {
        return city;
    }

    public String description() {
        return description;
    }

    public PetStatus status() {
        return status;
    }

    public List<String> photoPaths() {
        return photoPaths;
    }

    public Vaccination vaccination() {
        return extras.vaccination();
    }

    public az.miyav.domain.location.ListingLocation location() {
        return extras.location();
    }

    public ListingExtras extras() {
        return extras;
    }

    /** Paid placement at the top of Discover is still running. */
    public boolean premium(java.time.Instant now) {
        return extras.premium(now);
    }

    final void attachExtras(ListingExtras extras) {
        this.extras = extras == null ? ListingExtras.NONE : extras;
    }

    public Pet withStatus(PetStatus status) {
        return PetFactory.create(species, id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths, extras);
    }

    public Pet withPhotos(List<String> photoPaths) {
        return PetFactory.create(species, id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths, extras);
    }

    public Pet withExtras(ListingExtras extras) {
        return PetFactory.create(species, id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths, extras);
    }

    public Pet withDetails(
            String name,
            String breed,
            int ageMonths,
            PetGender gender,
            String city,
            String description,
            PetStatus status,
            List<String> photoPaths
    ) {
        return PetFactory.create(species, id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths, extras);
    }
}
