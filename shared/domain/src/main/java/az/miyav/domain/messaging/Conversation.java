package az.miyav.domain.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Conversation {
    private final UUID id;
    private final UUID ownerId;
    private final UUID otherUserId;
    private final Instant lastMessageAt;

    public Conversation(UUID id, UUID ownerId, UUID otherUserId, Instant lastMessageAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.otherUserId = Objects.requireNonNull(otherUserId, "otherUserId");
        this.lastMessageAt = Objects.requireNonNull(lastMessageAt, "lastMessageAt");
        if (ownerId.equals(otherUserId)) {
            throw new IllegalArgumentException("Conversation members must differ");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID otherUserId() {
        return otherUserId;
    }

    public Instant lastMessageAt() {
        return lastMessageAt;
    }

    public boolean includes(UUID userId) {
        return ownerId.equals(userId) || otherUserId.equals(userId);
    }
}
