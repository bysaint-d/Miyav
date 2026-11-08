package az.miyav.domain.validation;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.User;

public final class ProfileValidator {
    public void validate(User user) {
        if (user == null) {
            throw new ValidationException("validation.profile");
        }
        if (!Pet.SUPPORTED_CITIES.contains(user.city())) {
            throw new ValidationException("validation.city");
        }
    }
}
