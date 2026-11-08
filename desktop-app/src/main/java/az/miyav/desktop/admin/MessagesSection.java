package az.miyav.desktop.admin;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Messaging health without reading anyone's messages: conversations are private between members, and
 * the database does not let administrators read them. Problems reach moderators through reports.
 */
public final class MessagesSection extends AdminSection {
    private final HBox stats = new HBox(16);

    public MessagesSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "messages";
    }

    @Override
    public String icon() {
        return "💬";
    }

    @Override
    public String titleKey() {
        return "console.messages";
    }

    @Override
    public String subtitleKey() {
        return "console.messages_subtitle";
    }

    @Override
    protected Node build() {
        Button reports = new Button(text("console.open_reports"));
        reports.getStyleClass().add("primary");
        reports.setOnAction(event -> context.open("reports", null));
        VBox privacy = Components.card(text("console.messages_privacy_title"),
                Components.wrapped(text("console.messages_privacy_body"), "muted"), reports);
        privacy.getStyleClass().add("card-mint");
        return new VBox(18, stats, privacy);
    }

    @Override
    public void refresh() {
        context.load(() -> context.insights().overview(context.session()), overview -> stats.getChildren().setAll(
                Components.stat("💬", text("console.stat_conversations"), String.valueOf(overview.counts().conversations()),
                        text("console.stat_conversations_hint")),
                Components.stat("✉", text("console.stat_messages_week"), String.valueOf(overview.counts().messagesThisWeek()),
                        text("console.stat_messages_week_hint")),
                Components.stat("⚑", text("stats.reports"), String.valueOf(overview.counts().openReports()),
                        text("console.stat_reports_hint"))));
    }
}
