package az.miyav.domain.pet;

public enum PetStatus {
    DRAFT,
    PENDING,
    PUBLISHED,
    ADOPTED,
    REJECTED,
    ARCHIVED;

    public boolean isPubliclyVisible() {
        return this == PUBLISHED;
    }
}
