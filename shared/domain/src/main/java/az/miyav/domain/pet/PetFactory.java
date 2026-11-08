package az.miyav.domain.pet;

import java.util.List;
import java.util.UUID;

public final class PetFactory {
    private PetFactory() {
    }

    public static Pet create(
            PetSpecies species,
            UUID id,
            UUID ownerId,
            String name,
            String breed,
            int ageMonths,
            PetGender gender,
            String city,
            String description,
            PetStatus status,
            List<String> photoPaths
    ) {
        return create(species, id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths, ListingExtras.NONE);
    }

    public static Pet create(
            PetSpecies species,
            UUID id,
            UUID ownerId,
            String name,
            String breed,
            int ageMonths,
            PetGender gender,
            String city,
            String description,
            PetStatus status,
            List<String> photoPaths,
            ListingExtras extras
    ) {
        Pet pet = switch (species) {
            case DOG -> new Dog(id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths);
            case CAT -> new Cat(id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths);
            case BIRD -> new Bird(id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths);
            case OTHER -> new OtherPet(id, ownerId, name, breed, ageMonths, gender, city, description, status, photoPaths);
        };
        pet.attachExtras(extras);
        return pet;
    }
}
