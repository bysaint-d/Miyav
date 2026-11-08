package az.miyav.domain.walk;

/** Lifecycle of an owner's walking listing. */
public enum WalkListingStatus {
    OPEN("open"),
    ASSIGNED("assigned"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String databaseValue;

    WalkListingStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static WalkListingStatus fromDatabase(String value) {
        for (WalkListingStatus status : values()) {
            if (status.databaseValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown walk listing status: " + value);
    }

    /** Still waiting for, or already has, a walker. */
    public boolean active() {
        return this == OPEN || this == ASSIGNED;
    }
}
