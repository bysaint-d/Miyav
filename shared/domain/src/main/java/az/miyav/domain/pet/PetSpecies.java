package az.miyav.domain.pet;

public enum PetSpecies {
    DOG("dog"),
    CAT("cat"),
    BIRD("bird"),
    OTHER("other");

    private final String databaseValue;

    PetSpecies(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static PetSpecies fromDatabase(String value) {
        for (PetSpecies species : values()) {
            if (species.databaseValue.equals(value)) {
                return species;
            }
        }
        throw new IllegalArgumentException("Unknown species");
    }
}
