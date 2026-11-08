package az.miyav.domain.validation;

import az.miyav.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialValidatorTest {
    private final EmailValidator emails = new EmailValidator();
    private final PasswordValidator passwords = new PasswordValidator();

    @Test
    void acceptsANormalAddressAndPassword() {
        assertDoesNotThrow(() -> emails.validate("aysel@example.com"));
        assertDoesNotThrow(() -> passwords.validate("şifrə12345"));
    }

    @Test
    void rejectsMalformedCredentials() {
        assertThrows(ValidationException.class, () -> emails.validate("not-an-email"));
        assertThrows(ValidationException.class, () -> passwords.validate("short1"));
        assertThrows(ValidationException.class, () -> passwords.validate("letters-only"));
        assertThrows(ValidationException.class, () -> passwords.validate("password 123"));
    }
}
