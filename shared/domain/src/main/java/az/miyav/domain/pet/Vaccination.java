package az.miyav.domain.pet;

public enum Vaccination {
    YES("yes"),
    NO("no"),
    UNKNOWN("unknown");

    private final String databaseValue;

    Vaccination(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static Vaccination fromDatabase(String value) {
        for (Vaccination vaccination : values()) {
            if (vaccination.databaseValue.equals(value)) {
                return vaccination;
            }
        }
        return UNKNOWN;
    }
}
