package az.miyav.domain.user;

import az.miyav.common.error.ValidationException;

public enum UserRole {
    OWNER("owner"),
    SITTER("sitter"),
    ADMIN("admin");

    private final String databaseValue;

    UserRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static UserRole fromDatabase(String value) {
        if (value == null) {
            throw new ValidationException("validation.profile");
        }
        for (UserRole role : values()) {
            if (role.databaseValue.equals(value)) {
                return role;
            }
        }
        throw new ValidationException("validation.profile");
    }
}
