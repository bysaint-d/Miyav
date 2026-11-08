package az.miyav.domain.walk;

/** The kind of help a request asks for. Walking is MIYAV's core service; the others extend it. */
public enum CareService {
    WALKING("walking"),
    HOME_CARE("home_care"),
    BOARDING("boarding"),
    VISITS("visits");

    private final String databaseValue;

    CareService(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static CareService fromDatabase(String value) {
        for (CareService service : values()) {
            if (service.databaseValue.equals(value)) {
                return service;
            }
        }
        return WALKING;
    }
}
