package az.miyav.common.time;

import java.time.Instant;

public final class SystemTimeSource implements TimeSource {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
