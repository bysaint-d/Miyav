package az.miyav.domain.pet;

public enum PetGender {
    FEMALE("female"),
    MALE("male"),
    UNKNOWN("unknown");

    private final String databaseValue;

    PetGender(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static PetGender fromDatabase(String value) {
        for (PetGender gender : values()) {
            if (gender.databaseValue.equals(value)) {
                return gender;
            }
        }
        return UNKNOWN;
    }
}
