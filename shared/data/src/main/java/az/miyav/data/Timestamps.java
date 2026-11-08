package az.miyav.data;

import java.time.Instant;
import java.time.OffsetDateTime;

public final class Timestamps {
    private Timestamps() {
    }

    public static Instant parse(String value) {
        return OffsetDateTime.parse(value).toInstant();
    }
}
