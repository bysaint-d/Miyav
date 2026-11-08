package az.miyav.domain.moderation;

import az.miyav.domain.text.TextRules;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Report {
    private final UUID id;
    private final UUID reporterId;
    private final UUID reportedUserId;
    private final String reason;
    private final boolean resolved;
    private final Instant createdAt;

    public Report(UUID id, UUID reporterId, UUID reportedUserId, String reason, boolean resolved, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.reporterId = Objects.requireNonNull(reporterId, "reporterId");
        this.reportedUserId = Objects.requireNonNull(reportedUserId, "reportedUserId");
        if (reporterId.equals(reportedUserId)) {
            throw new IllegalArgumentException("A report cannot target its author");
        }
        this.reason = TextRules.required(reason, 10, 1500, "validation.pet");
        this.resolved = resolved;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID id() {
        return id;
    }

    public UUID reporterId() {
        return reporterId;
    }

    public UUID reportedUserId() {
        return reportedUserId;
    }

    public String reason() {
        return reason;
    }

    public boolean resolved() {
        return resolved;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
