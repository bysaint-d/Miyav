package az.miyav.domain.messaging;

import az.miyav.domain.text.TextRules;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Message {
    private final UUID id;
    private final UUID conversationId;
    private final UUID senderId;
    private final String body;
    private final Instant createdAt;

    public Message(UUID id, UUID conversationId, UUID senderId, String body, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.body = TextRules.required(body, 1, 4000, "validation.pet");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID id() {
        return id;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public UUID senderId() {
        return senderId;
    }

    public String body() {
        return body;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
