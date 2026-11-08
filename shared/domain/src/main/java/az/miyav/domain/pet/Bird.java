package az.miyav.domain.pet;

import java.util.List;
import java.util.UUID;

public final class Bird extends Pet {
    public Bird(
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
        super(id, ownerId, name, PetSpecies.BIRD, breed, ageMonths, gender, city, description, status, photoPaths);
    }

    @Override
    public CareProfile defaultCareProfile() {
        return new CareProfile(false, false, "pet.care.bird");
    }
}
