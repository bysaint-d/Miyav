package az.miyav.domain.validation;

import az.miyav.common.error.ValidationException;

import java.util.regex.Pattern;

public final class EmailValidator {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public void validate(String email) {
        if (email == null) {
            throw new ValidationException("validation.email");
        }
        String value = email.trim();
        if (value.length() > 254 || !EMAIL.matcher(value).matches()) {
            throw new ValidationException("validation.email");
        }
    }
}
