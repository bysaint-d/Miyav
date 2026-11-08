package az.miyav.domain.admin;

/** Who receives an admin push broadcast. */
public enum BroadcastAudience {
    ALL("all"),
    OWNERS("owners"),
    SITTERS("sitters");

    private final String databaseValue;

    BroadcastAudience(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static BroadcastAudience fromDatabase(String value) {
        for (BroadcastAudience audience : values()) {
            if (audience.databaseValue.equals(value)) {
                return audience;
            }
        }
        throw new IllegalArgumentException("Unknown broadcast audience: " + value);
    }
}
