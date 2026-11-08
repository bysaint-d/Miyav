package az.miyav.domain.validation;

import az.miyav.common.error.ValidationException;

public final class PasswordValidator {
    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 72;

    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new ValidationException("validation.password");
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char character = password.charAt(i);
            if (Character.isWhitespace(character)) {
                throw new ValidationException("validation.password");
            }
            letter = letter || Character.isLetter(character);
            digit = digit || Character.isDigit(character);
        }
        if (!letter || !digit) {
            throw new ValidationException("validation.password");
        }
    }
}
