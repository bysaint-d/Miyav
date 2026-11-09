package az.miyav.domain.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A chat message. It may name the walk listing it is about, and a walker's offer arrives as a price card
 * ({@link #pricePerHour()}), with or without text.
 */
public final class Message {
    public static final int MAX_LENGTH = 2000;

    private final UUID id;
    private final UUID conversationId;
    private final UUID senderId;
    private final String body;
    private final UUID walkListingId;
    private final BigDecimal pricePerHour;
    private final String imagePath;
    private final Instant createdAt;

    public Message(UUID id, UUID conversationId, UUID senderId, String body, UUID walkListingId,
                   BigDecimal pricePerHour, String imagePath, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.body = body == null ? "" : body;
        this.walkListingId = walkListingId;
        this.pricePerHour = pricePerHour;
        this.imagePath = imagePath == null || imagePath.isBlank() ? null : imagePath;
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

    public Optional<UUID> walkListingId() {
        return Optional.ofNullable(walkListingId);
    }

    public Optional<BigDecimal> pricePerHour() {
        return Optional.ofNullable(pricePerHour);
    }

    public boolean image() {
        return imagePath != null;
    }

    /** Storage path in the private "messages" bucket; readable only by the two participants. */
    public Optional<String> imagePath() {
        return Optional.ofNullable(imagePath);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean sentBy(UUID userId) {
        return senderId.equals(userId);
    }

    public boolean priceOffer() {
        return pricePerHour != null;
    }
}
