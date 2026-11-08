package az.miyav.common.time;

import java.time.Instant;

public interface TimeSource {
    Instant now();
}
