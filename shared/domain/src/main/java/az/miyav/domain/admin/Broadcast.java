package az.miyav.domain.admin;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A push notification an administrator sent, as listed in the console history. */
public final class Broadcast {
    public static final int MAX_TITLE = 80;
    public static final int MAX_BODY = 500;

    private final UUID id;
    private final String title;
    private final String body;
    private final BroadcastAudience audience;
    private final String city;
    private final int recipients;
    private final Instant sentAt;
    private final String senderName;

    public Broadcast(UUID id, String title, String body, BroadcastAudience audience, String city, int recipients,
                     Instant sentAt, String senderName) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.audience = Objects.requireNonNull(audience, "audience");
        this.city = city == null || city.isBlank() ? null : city;
        this.recipients = Math.max(0, recipients);
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        this.senderName = senderName == null ? "" : senderName;
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public BroadcastAudience audience() {
        return audience;
    }

    /** Empty when sent to every city. */
    public Optional<String> city() {
        return Optional.ofNullable(city);
    }

    public int recipients() {
        return recipients;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public String senderName() {
        return senderName;
    }
}
