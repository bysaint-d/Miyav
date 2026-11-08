package az.miyav.domain.walk;

import az.miyav.domain.pet.PetSpecies;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class WalkSession {
    public enum Status {
        ACTIVE("active"), PAUSED("paused"), ENDED("ended");

        private final String databaseValue;

        Status(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static Status fromDatabase(String value) {
            for (Status status : values()) {
                if (status.databaseValue.equals(value)) {
                    return status;
                }
            }
            return ENDED;
        }
    }

    private final UUID id;
    private final Status status;
    private final PetSpecies species;
    private final String petName;
    private final String message;
    private final Instant startedAt;
    private final Instant expiresAt;

    public WalkSession(UUID id, Status status, PetSpecies species, String petName, String message,
                       Instant startedAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.status = Objects.requireNonNull(status, "status");
        this.species = Objects.requireNonNull(species, "species");
        this.petName = petName == null ? "" : petName;
        this.message = message == null ? "" : message;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public Status status() {
        return status;
    }

    public PetSpecies species() {
        return species;
    }

    public String petName() {
        return petName;
    }

    public String message() {
        return message;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean open() {
        return status != Status.ENDED;
    }
}
