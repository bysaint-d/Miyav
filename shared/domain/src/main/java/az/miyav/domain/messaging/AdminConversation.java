package az.miyav.domain.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One member-to-member conversation as the moderation console lists it. */
public final class AdminConversation {
    private final UUID id;
    private final UUID firstId;
    private final String firstName;
    private final UUID secondId;
    private final String secondName;
    private final Instant lastMessageAt;
    private final String lastBody;
    private final boolean lastHasImage;
    private final int messageCount;

    public AdminConversation(UUID id, UUID firstId, String firstName, UUID secondId, String secondName,
                             Instant lastMessageAt, String lastBody, boolean lastHasImage, int messageCount) {
        this.id = Objects.requireNonNull(id, "id");
        this.firstId = Objects.requireNonNull(firstId, "firstId");
        this.firstName = firstName == null ? "" : firstName;
        this.secondId = Objects.requireNonNull(secondId, "secondId");
        this.secondName = secondName == null ? "" : secondName;
        this.lastMessageAt = lastMessageAt;
        this.lastBody = lastBody == null ? "" : lastBody;
        this.lastHasImage = lastHasImage;
        this.messageCount = messageCount;
    }

    public UUID id() {
        return id;
    }

    public UUID firstId() {
        return firstId;
    }

    public String firstName() {
        return firstName;
    }

    public UUID secondId() {
        return secondId;
    }

    public String secondName() {
        return secondName;
    }

    public Instant lastMessageAt() {
        return lastMessageAt;
    }

    public String lastBody() {
        return lastBody;
    }

    public boolean lastHasImage() {
        return lastHasImage;
    }

    public int messageCount() {
        return messageCount;
    }

    /** The name of {@code userId} in this conversation, or empty when they are not part of it. */
    public String nameOf(UUID userId) {
        if (firstId.equals(userId)) {
            return firstName;
        }
        return secondId.equals(userId) ? secondName : "";
    }
}
