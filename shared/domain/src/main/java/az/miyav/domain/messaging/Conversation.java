package az.miyav.domain.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One chat as the signed-in user sees it in the inbox: the other person and the latest message. */
public final class Conversation {
    private final UUID id;
    private final UUID otherUserId;
    private final String otherName;
    private final Instant lastMessageAt;
    private final String lastBody;
    private final boolean lastFromMe;
    private final BigDecimal lastPrice;
    private final boolean unread;

    public Conversation(UUID id, UUID otherUserId, String otherName, Instant lastMessageAt, String lastBody,
                        boolean lastFromMe, BigDecimal lastPrice, boolean unread) {
        this.id = Objects.requireNonNull(id, "id");
        this.otherUserId = Objects.requireNonNull(otherUserId, "otherUserId");
        this.otherName = otherName == null ? "" : otherName.trim();
        this.lastMessageAt = Objects.requireNonNull(lastMessageAt, "lastMessageAt");
        this.lastBody = lastBody == null ? "" : lastBody;
        this.lastFromMe = lastFromMe;
        this.lastPrice = lastPrice;
        this.unread = unread && !lastFromMe;
    }

    public UUID id() {
        return id;
    }

    public UUID otherUserId() {
        return otherUserId;
    }

    public String otherName() {
        return otherName;
    }

    public Instant lastMessageAt() {
        return lastMessageAt;
    }

    public String lastBody() {
        return lastBody;
    }

    public boolean lastFromMe() {
        return lastFromMe;
    }

    /** Set when the latest message is a price offer. */
    public Optional<BigDecimal> lastPrice() {
        return Optional.ofNullable(lastPrice);
    }

    /** The other person wrote last and the user has not opened the chat since. */
    public boolean unread() {
        return unread;
    }
}
