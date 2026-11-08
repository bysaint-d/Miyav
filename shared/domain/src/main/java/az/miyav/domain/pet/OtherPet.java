package az.miyav.domain.pet;

import java.util.List;
import java.util.UUID;

public final class OtherPet extends Pet {
    public OtherPet(
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
        super(id, ownerId, name, PetSpecies.OTHER, breed, ageMonths, gender, city, description, status, photoPaths);
    }

    @Override
    public CareProfile defaultCareProfile() {
        return new CareProfile(false, false, "pet.care.other");
    }
}
