package az.miyav.desktop.admin;

import az.miyav.domain.messaging.AdminConversation;
import az.miyav.domain.messaging.Message;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.UUID;

/**
 * Message moderation: every member conversation with a search over names and text, and the full thread
 * (photos, price offers and walk steps) of the selected one. Members agree to this in the Terms of Service;
 * the server writes each opened conversation to the audit log.
 */
public final class MessagesSection extends AdminSection {
    private final HBox stats = new HBox(16);
    private final TextField search = new TextField();
    private final VBox list = new VBox(8);
    private final VBox thread = new VBox(10);
    private final ScrollPane threadScroll = new ScrollPane(thread);
    private final PauseTransition typing = new PauseTransition(Duration.millis(350));
    private List<AdminConversation> conversations = List.of();
    private UUID selected;
    private UUID focus;

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
        search.setPromptText(text("console.messages_search"));
        HBox.setHgrow(search, Priority.ALWAYS);
        typing.setOnFinished(event -> loadConversations());
        search.textProperty().addListener((observable, before, now) -> typing.playFromStart());
        Button reports = new Button(text("console.open_reports"));
        reports.getStyleClass().add("ghost");
        reports.setOnAction(event -> context.open("reports", null));

        ScrollPane listScroll = new ScrollPane(list);
        listScroll.setFitToWidth(true);
        listScroll.setPrefWidth(360);
        listScroll.setMinWidth(320);
        listScroll.setPrefHeight(620);
        listScroll.getStyleClass().add("detail-scroll");
        list.setPadding(new Insets(4));

        threadScroll.setFitToWidth(true);
        threadScroll.setPrefHeight(620);
        threadScroll.getStyleClass().add("detail-scroll");
        thread.setPadding(new Insets(4, 12, 4, 4));
        VBox threadCard = Components.card(threadScroll);
        HBox.setHgrow(threadCard, Priority.ALWAYS);
        showThread(null, List.of());

        HBox body = new HBox(16, listScroll, threadCard);
        Label note = Components.wrapped(text("console.messages_audit_note"), "caption");
        return new VBox(14, stats, Components.row(10, search, reports), note, body);
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
        context.members(members -> {
        });
        loadConversations();
    }

    /** Opens the conversations of one member, for example from a report. */
    @Override
    public void focus(UUID memberId) {
        focus = memberId;
        String name = context.memberName(memberId);
        if (!name.isEmpty()) {
            search.setText(name);
        }
    }

    private void loadConversations() {
        String query = search.getText();
        context.load(() -> context.graph().adminMessages().conversations(context.session(), query), loaded -> {
            conversations = loaded;
            renderList();
            if (focus != null) {
                for (AdminConversation conversation : loaded) {
                    if (conversation.firstId().equals(focus) || conversation.secondId().equals(focus)) {
                        open(conversation);
                        break;
                    }
                }
                focus = null;
            }
        });
    }

    private void renderList() {
        list.getChildren().clear();
        for (AdminConversation conversation : conversations) {
            list.getChildren().add(row(conversation));
        }
        if (conversations.isEmpty()) {
            list.getChildren().add(Components.empty("💬", text("console.messages_empty_title"),
                    search.getText().isBlank() ? text("console.messages_empty_body") : text("console.empty_filter")));
        }
    }

    private Node row(AdminConversation conversation) {
        String preview = conversation.lastBody().isBlank() && conversation.lastHasImage()
                ? "📷 " + text("console.messages_photo") : conversation.lastBody();
        Label names = Components.label(name(conversation.firstName()) + "  ↔  " + name(conversation.secondName()), "cell-title");
        names.setWrapText(true);
        Label last = Components.label(preview, "muted");
        last.setMaxHeight(40);
        last.setWrapText(true);
        Label meta = Components.label(Components.ago(conversation.lastMessageAt(), context.now(), context) + "  ·  "
                + text("console.messages_count", conversation.messageCount()), "caption");
        VBox card = new VBox(4, names, last, meta);
        card.getStyleClass().add("card");
        card.setStyle("-fx-padding: 12 14 12 14; -fx-cursor: hand;");
        if (conversation.id().equals(selected)) {
            card.getStyleClass().add("card-mint");
        }
        card.setOnMouseClicked(event -> open(conversation));
        return card;
    }

    private void open(AdminConversation conversation) {
        selected = conversation.id();
        renderList();
        thread.getChildren().setAll(Components.label(text("console.loading"), "muted"));
        context.load(() -> context.graph().adminMessages().read(context.session(), conversation.id()),
                messages -> showThread(conversation, messages));
    }

    private void showThread(AdminConversation conversation, List<Message> messages) {
        thread.getChildren().clear();
        if (conversation == null) {
            thread.setAlignment(Pos.CENTER);
            thread.getChildren().add(Components.empty("🔎", text("console.messages_select_title"),
                    text("console.messages_select_body")));
            return;
        }
        thread.setAlignment(Pos.TOP_LEFT);
        Button first = profile(conversation.firstId(), conversation.firstName());
        Button second = profile(conversation.secondId(), conversation.secondName());
        thread.getChildren().addAll(
                Components.row(8, Components.label(text("console.messages_between"), "caption"), first,
                        Components.label("↔", "caption"), second),
                Components.label(text("console.messages_count", messages.size()) + "  ·  "
                        + text("console.messages_audited"), "caption"));
        for (Message message : messages) {
            thread.getChildren().add(bubble(conversation, message));
        }
        threadScroll.layout();
        threadScroll.setVvalue(1.0);
    }

    private Button profile(UUID userId, String name) {
        Button button = new Button(name(name));
        button.getStyleClass().add("ghost");
        button.setOnAction(event -> context.open("users", userId));
        return button;
    }

    private Node bubble(AdminConversation conversation, Message message) {
        if (message.event().isPresent()) {
            Label event = Components.label(message.body(), "cell-title");
            event.setWrapText(true);
            HBox box = new HBox(event);
            box.setAlignment(Pos.CENTER);
            box.getStyleClass().addAll("card", "card-mint");
            box.setStyle("-fx-padding: 8 14 8 14;");
            HBox row = new HBox(box);
            row.setAlignment(Pos.CENTER);
            return row;
        }
        boolean first = message.senderId().equals(conversation.firstId());
        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("card");
        if (first) {
            bubble.getStyleClass().add("card-peach");
        }
        bubble.setStyle("-fx-padding: 10 14 10 14;");
        bubble.setMaxWidth(520);
        bubble.getChildren().add(Components.label(name(conversation.nameOf(message.senderId())) + "  ·  "
                + Components.dateTime(message.createdAt(), context.locale()), "caption"));
        message.pricePerHour().ifPresent(price -> bubble.getChildren().add(
                Components.label("💰 " + text("console.messages_price", price.stripTrailingZeros().toPlainString()), "cell-title")));
        if (message.image()) {
            ImageView photo = new ImageView();
            bubble.getChildren().add(photo);
            context.load(() -> context.graph().adminMessages().photo(context.session(), message), bytes -> {
                var image = Components.image(bytes, 640, 640);
                if (image != null) {
                    Components.fill(photo, image, 260, 200, 12);
                    photo.setOnMouseClicked(event -> az.miyav.desktop.PhotoViewer.open(context.window(), text("console.messages"),
                            List.of(message.imagePath().orElseThrow()), 0,
                            path -> context.graph().adminMessages().photo(context.session(), message)));
                    photo.setStyle("-fx-cursor: hand;");
                }
            }, code -> bubble.getChildren().add(Components.label(text("console.messages_photo_unavailable"), "caption")));
        }
        if (!message.body().isBlank()) {
            Label body = Components.wrapped(message.body());
            body.setMaxWidth(490);
            bubble.getChildren().add(body);
        }
        HBox row = new HBox(bubble);
        row.setAlignment(first ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        return row;
    }

    private String name(String name) {
        return name == null || name.isBlank() ? text("console.messages_unknown") : name;
    }
}
