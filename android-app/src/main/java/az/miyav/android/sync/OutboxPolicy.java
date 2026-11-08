package az.miyav.android.sync;

import java.util.UUID;

/** Decides what the outbox does with a failed operation and encodes the queued adoption payload. */
public final class OutboxPolicy {
    public enum Decision { RETRY, DROP }

    public static final int MAX_ATTEMPTS = 20;

    private OutboxPolicy() {
    }

    public static Decision afterFailure(String code, int attemptsSoFar) {
        boolean transientFailure = "network.unavailable".equals(code)
                || "auth.session_expired".equals(code)
                || "auth.unavailable".equals(code);
        if (transientFailure && attemptsSoFar + 1 < MAX_ATTEMPTS) {
            return Decision.RETRY;
        }
        return Decision.DROP;
    }

    public static String encodeAdoption(UUID listingId, String message) {
        return listingId + "|" + message;
    }

    public static Adoption decodeAdoption(String payload) {
        if (payload == null) {
            return null;
        }
        int split = payload.indexOf('|');
        if (split != 36) {
            return null;
        }
        try {
            return new Adoption(UUID.fromString(payload.substring(0, split)), payload.substring(split + 1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static final class Adoption {
        public final UUID listingId;
        public final String message;

        Adoption(UUID listingId, String message) {
            this.listingId = listingId;
            this.message = message;
        }
    }
}
