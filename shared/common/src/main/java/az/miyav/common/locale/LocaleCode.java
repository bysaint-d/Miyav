package az.miyav.common.locale;

import java.util.Locale;

public enum LocaleCode {
    AZ("az"),
    EN("en"),
    RU("ru");

    private final String tag;

    LocaleCode(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public Locale locale() {
        return Locale.forLanguageTag(tag);
    }

    public static LocaleCode fromTag(String tag) {
        if (tag == null) {
            return AZ;
        }
        return switch (tag.trim().toLowerCase(Locale.ROOT)) {
            case "en" -> EN;
            case "ru" -> RU;
            default -> AZ;
        };
    }
}
