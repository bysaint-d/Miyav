package az.miyav.domain.validation;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.pet.Pet;

public final class PetValidator {
    public void validate(Pet pet) {
        if (pet == null) {
            throw new ValidationException("validation.pet");
        }
        if (!Pet.SUPPORTED_CITIES.contains(pet.city())) {
            throw new ValidationException("validation.city");
        }
    }
}
