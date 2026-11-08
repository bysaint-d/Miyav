package az.miyav.domain.notification;

import az.miyav.domain.text.TextRules;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Notification {
    private final UUID id;
    private final UUID userId;
    private final NotificationType type;
    private final String title;
    private final String body;
    private final Instant createdAt;
    private final Instant readAt;
    private final UUID conversationId;

    public Notification(
            UUID id,
            UUID userId,
            NotificationType type,
            String title,
            String body,
            Instant createdAt,
            Instant readAt
    ) {
        this(id, userId, type, title, body, createdAt, readAt, null);
    }

    /** {@code conversationId} is set for new chat messages and walk offers, so the chat can be opened. */
    public Notification(
            UUID id,
            UUID userId,
            NotificationType type,
            String title,
            String body,
            Instant createdAt,
            Instant readAt,
            UUID conversationId
    ) {
        this.conversationId = conversationId;
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.type = type == null ? NotificationType.SYSTEM : type;
        this.title = TextRules.required(title, 1, 160, "validation.pet");
        this.body = TextRules.required(body, 1, 2000, "validation.pet");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.readAt = readAt;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public NotificationType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant readAt() {
        return readAt;
    }

    public boolean unread() {
        return readAt == null;
    }

    public Optional<UUID> conversationId() {
        return Optional.ofNullable(conversationId);
    }
}
