package az.miyav.data;

import az.miyav.common.error.PersistenceException;
import az.miyav.domain.pet.PetStatus;

public final class ListingStatusMapper {
    private ListingStatusMapper() {
    }

    public static PetStatus toDomain(String databaseValue) {
        return switch (databaseValue) {
            case "draft" -> PetStatus.DRAFT;
            case "active" -> PetStatus.PUBLISHED;
            case "adopted" -> PetStatus.ADOPTED;
            case "deleted" -> PetStatus.ARCHIVED;
            default -> throw new PersistenceException("schema.not_ready");
        };
    }

    public static String toDatabase(PetStatus status) {
        return switch (status) {
            case DRAFT -> "draft";
            case PUBLISHED -> "active";
            case ADOPTED -> "adopted";
            case ARCHIVED -> "deleted";
            default -> throw new PersistenceException("schema.not_ready");
        };
    }
}
