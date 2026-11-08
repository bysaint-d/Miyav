package az.miyav.desktop.admin;

import az.miyav.common.locale.LocaleCode;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Console language, the signed-in administrator, and the privacy rules the console follows. */
public final class SettingsSection extends AdminSection {
    private final Consumer<LocaleCode> language;
    private final Runnable signOut;
    private final Runnable publicSite;

    public SettingsSection(ConsoleContext context, Consumer<LocaleCode> language, Runnable signOut, Runnable publicSite) {
        super(context);
        this.language = language;
        this.signOut = signOut;
        this.publicSite = publicSite;
    }

    @Override
    public String id() {
        return "settings";
    }

    @Override
    public String icon() {
        return "⚙";
    }

    @Override
    public String titleKey() {
        return "console.settings";
    }

    @Override
    public String subtitleKey() {
        return "console.settings_subtitle";
    }

    @Override
    protected Node build() {
        String email = context.graph().session().session().map(session -> session.email()).orElse("—");
        VBox account = Components.card(text("console.settings_account"),
                Components.label(email, "cell-title"),
                Components.row(8, button("console.public_site", "ghost", publicSite), button("auth.logout", "danger", signOut)));
        VBox languages = Components.card(text("console.settings_language"),
                Components.row(8, locale(LocaleCode.AZ, "Azərbaycan"), locale(LocaleCode.EN, "English"), locale(LocaleCode.RU, "Русский")));
        VBox privacy = Components.card(text("console.settings_privacy"),
                Components.wrapped(text("console.settings_privacy_body"), "muted"));
        privacy.getStyleClass().add("card-mint");
        return new VBox(18, account, languages, privacy);
    }

    private Button locale(LocaleCode code, String name) {
        Button button = new Button(name);
        button.getStyleClass().add(context.graph().catalog().locale() == code ? "primary" : "ghost");
        button.setOnAction(event -> language.accept(code));
        return button;
    }

    private Button button(String key, String style, Runnable action) {
        Button button = new Button(text(key));
        button.getStyleClass().add(style);
        button.setOnAction(event -> action.run());
        return button;
    }

    @Override
    public void refresh() {
    }
}
