package az.miyav.domain.text;

import az.miyav.common.error.ValidationException;

public final class TextRules {
    private TextRules() {
    }

    public static String required(String value, int min, int max, String code) {
        if (value == null) {
            throw new ValidationException(code);
        }
        String trimmed = value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new ValidationException(code);
        }
        return trimmed;
    }

    public static String optional(String value, int max, String code) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new ValidationException(code);
        }
        return trimmed;
    }
}
