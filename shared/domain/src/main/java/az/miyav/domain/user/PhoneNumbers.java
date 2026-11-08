package az.miyav.domain.user;

import java.util.Optional;

/**
 * Normalises a phone number the user entered into international digits for tel: and wa.me links.
 * Azerbaijani local formats (050..., 50...) get the 994 country code; anything that cannot be
 * normalised safely is rejected instead of guessed.
 */
public final class PhoneNumbers {
    private PhoneNumbers() {
    }

    public static Optional<String> internationalDigits(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        boolean plus = trimmed.startsWith("+");
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char character = trimmed.charAt(i);
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (" -().+".indexOf(character) < 0) {
                return Optional.empty();
            }
        }
        String value = digits.toString();
        if (value.startsWith("00")) {
            value = value.substring(2);
            plus = true;
        }
        if (!plus) {
            if (value.length() == 10 && value.startsWith("0")) {
                value = "994" + value.substring(1);
            } else if (value.length() == 9) {
                value = "994" + value;
            }
        }
        if (value.startsWith("994") && value.length() != 12) {
            return Optional.empty();
        }
        if (value.length() < 8 || value.length() > 15 || value.startsWith("0")) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public static boolean valid(String raw) {
        return raw == null || raw.isBlank() || internationalDigits(raw).isPresent();
    }
}
