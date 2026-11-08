package az.miyav.common.locale;

public interface MessageCatalog {
    LocaleCode locale();

    String text(String code);
}
