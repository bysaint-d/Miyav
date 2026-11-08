package az.miyav.domain.adoption;

import az.miyav.domain.text.TextRules;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AdoptionApplication {
    private final UUID id;
    private final UUID petId;
    private final UUID applicantId;
    private final String message;
    private final ApplicationStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public AdoptionApplication(
            UUID id,
            UUID petId,
            UUID applicantId,
            String message,
            ApplicationStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.petId = Objects.requireNonNull(petId, "petId");
        this.applicantId = Objects.requireNonNull(applicantId, "applicantId");
        this.message = TextRules.required(message, 10, 2000, "validation.pet");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public UUID id() {
        return id;
    }

    public UUID petId() {
        return petId;
    }

    public UUID applicantId() {
        return applicantId;
    }

    public String message() {
        return message;
    }

    public ApplicationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public AdoptionApplication withStatus(ApplicationStatus status, Instant updatedAt) {
        return new AdoptionApplication(id, petId, applicantId, message, status, createdAt, updatedAt);
    }
}
