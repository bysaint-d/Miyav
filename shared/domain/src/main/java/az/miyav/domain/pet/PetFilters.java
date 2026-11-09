package az.miyav.domain.pet;

import java.util.Objects;

/** The detailed listing filter: gender, age band, vaccinated only, and order. */
public final class PetFilters {
    public static final PetFilters NONE = new PetFilters(null, AgeRange.ANY, false, PetSort.NEWEST);

    private final PetGender gender;
    private final AgeRange age;
    private final boolean vaccinatedOnly;
    private final PetSort sort;

    public PetFilters(PetGender gender, AgeRange age, boolean vaccinatedOnly, PetSort sort) {
        this.gender = gender == PetGender.UNKNOWN ? null : gender;
        this.age = age == null ? AgeRange.ANY : age;
        this.vaccinatedOnly = vaccinatedOnly;
        this.sort = sort == null ? PetSort.NEWEST : sort;
    }

    /** Null means any gender. */
    public PetGender gender() {
        return gender;
    }

    public AgeRange age() {
        return age;
    }

    public boolean vaccinatedOnly() {
        return vaccinatedOnly;
    }

    public PetSort sort() {
        return sort;
    }

    /** How many filters differ from the default; shown as a badge on the filter button. */
    public int activeCount() {
        int count = 0;
        if (gender != null) {
            count++;
        }
        if (age != AgeRange.ANY) {
            count++;
        }
        if (vaccinatedOnly) {
            count++;
        }
        if (sort != PetSort.NEWEST) {
            count++;
        }
        return count;
    }

    public boolean matches(Pet pet) {
        return (gender == null || pet.gender() == gender)
                && age.contains(pet.ageMonths())
                && (!vaccinatedOnly || pet.vaccination() == Vaccination.YES);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PetFilters filters)) {
            return false;
        }
        return vaccinatedOnly == filters.vaccinatedOnly && gender == filters.gender && age == filters.age
                && sort == filters.sort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gender, age, vaccinatedOnly, sort);
    }
}
