package az.miyav.desktop.user;

import az.miyav.desktop.PhotoViewer;
import az.miyav.desktop.admin.Components;
import az.miyav.domain.messaging.Conversation;
import az.miyav.domain.messaging.Message;
import az.miyav.domain.repository.LiveUpdates;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The member's chats: inbox on the left, the open conversation on the right. New messages arrive over
 * Realtime; a slow poll covers the gaps when the live connection is down.
 */
public final class MessagesPage extends UserPage {
    private static final Duration POLL = Duration.seconds(6);
    private static final Duration LIVE_POLL = Duration.seconds(30);
    private final ListView<Conversation> inbox = new ListView<>();
    private final Label chatTitle = Components.label("", "card-title");
    private final Label liveBadge = Components.badge("", Components.Tone.SAGE);
    private final VBox thread = new VBox(8);
    private final ScrollPane threadScroll = new ScrollPane(thread);
    private final TextField input = new TextField();
    private final Button send = new Button();
    private final Button photo = new Button("📷");
    private final Map<String, Image> photos = new ConcurrentHashMap<>();
    private final Timeline poll = new Timeline();
    private LiveUpdates.Subscription live = LiveUpdates.NONE;
    private Conversation open;
    private UUID lastMessage;
    private UUID pendingFocus;
    private Conversation pendingPlaceholder;
    private boolean loading;

    public MessagesPage(UserContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "messages";
    }

    @Override
    public String titleKey() {
        return "user.nav.messages";
    }

    @Override
    public String icon() {
        return "💬";
    }

    /** A chat that may have no messages yet (just opened from a listing), so it is not in the inbox. */
    public void prepare(UUID room, UUID otherUserId, String otherName) {
        pendingPlaceholder = new Conversation(room, otherUserId, otherName, Instant.now(), "", false, null, false);
    }

    @Override
    protected Node build() {
        inbox.setCellFactory(view -> new ConversationCell());
        inbox.setPlaceholder(Components.wrapped(text("user.messages.empty"), "muted"));
        inbox.getSelectionModel().selectedItemProperty().addListener((observable, before, picked) -> {
            if (picked != null && (open == null || !open.id().equals(picked.id()))) {
                show(picked);
            }
        });
        inbox.setPrefWidth(320);

        thread.setPadding(new Insets(12));
        threadScroll.setFitToWidth(true);
        threadScroll.getStyleClass().add("detail-scroll");
        input.setPromptText(text("user.messages.hint"));
        input.setOnAction(event -> sendText());
        HBox.setHgrow(input, Priority.ALWAYS);
        send.setText(text("user.messages.send"));
        send.getStyleClass().add("primary");
        send.setOnAction(event -> sendText());
        photo.getStyleClass().add("ghost");
        photo.setTooltip(new javafx.scene.control.Tooltip(text("user.messages.photo_send")));
        photo.setOnAction(event -> sendPhoto());
        liveBadge.setText(text("user.messages.live"));
        liveBadge.setVisible(false);
        HBox header = Components.row(10, chatTitle, Components.spacer(), liveBadge);
        header.setPadding(new Insets(4, 4, 8, 4));
        HBox compose = Components.row(8, photo, input, send);
        compose.setPadding(new Insets(8, 0, 0, 0));
        BorderPane chat = new BorderPane(threadScroll, header, null, compose, null);
        chat.getStyleClass().add("card");
        enableCompose(false);
        chatTitle.setText(text("user.messages.select"));

        SplitPane split = new SplitPane(inbox, chat);
        split.setDividerPositions(0.3);
        split.getStyleClass().add("split");
        split.setPrefHeight(640);
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.getKeyFrames().setAll(new KeyFrame(POLL, event -> tick()));
        return split;
    }

    @Override
    public void refresh() {
        loadInbox();
        poll.play();
    }

    @Override
    public void focus(UUID id) {
        pendingFocus = id;
        applyFocus();
    }

    @Override
    public void hidden() {
        poll.stop();
        live.close();
        live = LiveUpdates.NONE;
    }

    private void tick() {
        Duration wanted = live.live() ? LIVE_POLL : POLL;
        if (!poll.getKeyFrames().get(0).getTime().equals(wanted)) {
            poll.stop();
            poll.getKeyFrames().setAll(new KeyFrame(wanted, event -> tick()));
            poll.play();
        }
        liveBadge.setVisible(live.live());
        loadInbox();
        if (open != null) {
            loadThread(false);
        }
    }

    private void loadInbox() {
        context.load(() -> context.graph().messaging().inbox(context.session()), conversations -> {
            UUID keep = open == null ? null : open.id();
            List<Conversation> shown = new ArrayList<>(conversations);
            if (pendingPlaceholder != null && shown.stream().noneMatch(c -> c.id().equals(pendingPlaceholder.id()))) {
                shown.add(0, pendingPlaceholder);
            }
            inbox.getItems().setAll(shown);
            if (keep != null) {
                shown.stream().filter(c -> c.id().equals(keep)).findFirst()
                        .ifPresent(c -> inbox.getSelectionModel().select(c));
            }
            applyFocus();
            context.badgesChanged();
        }, code -> {
        });
    }

    private void applyFocus() {
        UUID target = pendingFocus;
        if (target == null) {
            return;
        }
        for (Conversation conversation : inbox.getItems()) {
            if (conversation.id().equals(target)) {
                pendingFocus = null;
                inbox.getSelectionModel().select(conversation);
                inbox.scrollTo(conversation);
                return;
            }
        }
    }

    private void show(Conversation conversation) {
        open = conversation;
        lastMessage = null;
        chatTitle.setText(conversation.otherName() == null || conversation.otherName().isBlank()
                ? text("user.messages.unknown") : conversation.otherName());
        thread.getChildren().setAll(Components.label("…", "muted"));
        enableCompose(true);
        live.close();
        UUID room = conversation.id();
        live = context.graph().live().watchConversation(() -> context.graph().session().viewer(), room,
                () -> Platform.runLater(() -> {
                    if (open != null && open.id().equals(room)) {
                        loadThread(true);
                        loadInbox();
                    }
                }));
        loadThread(true);
    }

    private void loadThread(boolean markRead) {
        Conversation target = open;
        if (target == null || loading) {
            return;
        }
        loading = true;
        context.load(() -> {
            List<Message> history = context.graph().messaging().history(context.session(), target.id());
            if (markRead || target.unread()) {
                try {
                    context.graph().messaging().markRead(context.session(), target.id());
                } catch (RuntimeException ignored) {
                    // Unread markers are cosmetic; the chat still opens.
                }
            }
            return history;
        }, history -> {
            loading = false;
            if (open == null || !open.id().equals(target.id())) {
                return;
            }
            UUID newest = history.isEmpty() ? null : history.get(history.size() - 1).id();
            if (newest != null && newest.equals(lastMessage)) {
                return;
            }
            lastMessage = newest;
            render(history);
            context.badgesChanged();
        }, code -> {
            loading = false;
            context.status(text(code));
        });
    }

    private void render(List<Message> history) {
        UUID me = context.graph().session().session().map(session -> session.userId()).orElse(null);
        thread.getChildren().clear();
        if (history.isEmpty()) {
            thread.getChildren().add(Components.wrapped(text("user.messages.first"), "muted"));
        }
        for (Message message : history) {
            thread.getChildren().add(bubble(message, message.sentBy(me)));
        }
        Platform.runLater(() -> threadScroll.setVvalue(1.0));
    }

    private Node bubble(Message message, boolean mine) {
        VBox bubble = new VBox(6);
        bubble.getStyleClass().add("card");
        if (mine) {
            bubble.getStyleClass().add("card-mint");
        }
        bubble.setMaxWidth(460);
        bubble.setPadding(new Insets(10, 14, 8, 14));
        message.pricePerHour().ifPresent(price -> bubble.getChildren().add(
                Components.label(text("user.messages.price", money(price)), "cell-title")));
        message.imagePath().ifPresent(path -> bubble.getChildren().add(photoNode(path)));
        if (!message.body().isBlank()) {
            bubble.getChildren().add(Components.wrapped(message.body()));
        }
        Label time = Components.label(Components.dateTime(message.createdAt(), context.locale()), "caption");
        HBox timeRow = new HBox(time);
        timeRow.setAlignment(Pos.CENTER_RIGHT);
        bubble.getChildren().add(timeRow);
        HBox row = new HBox(bubble);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private Node photoNode(String path) {
        ImageView view = new ImageView();
        view.setFitWidth(260);
        view.setFitHeight(260);
        view.setPreserveRatio(true);
        view.setCursor(Cursor.HAND);
        view.setOnMouseClicked(event -> PhotoViewer.open(context.window(), chatTitle.getText(), List.of(path), 0,
                source -> context.graph().messaging().photo(context.session(), source)));
        Image cached = photos.get(path);
        if (cached != null) {
            view.setImage(cached);
            return view;
        }
        Label placeholder = Components.label(text("user.messages.photo"), "muted");
        VBox box = new VBox(placeholder, view);
        context.load(() -> Components.image(context.graph().messaging().photo(context.session(), path), 520, 520), image -> {
            if (image != null) {
                photos.put(path, image);
                view.setImage(image);
                box.getChildren().remove(placeholder);
            }
        }, code -> {
        });
        return box;
    }

    private void sendText() {
        Conversation target = open;
        String body = input.getText() == null ? "" : input.getText().trim();
        if (target == null || body.isEmpty()) {
            return;
        }
        enableCompose(false);
        context.load(() -> context.graph().messaging().send(context.session(), target.id(), body, null), sent -> {
            input.clear();
            enableCompose(true);
            input.requestFocus();
            pendingPlaceholder = null;
            loadThread(false);
            loadInbox();
        }, code -> {
            enableCompose(true);
            context.status(text(code));
        });
    }

    private void sendPhoto() {
        Conversation target = open;
        if (target == null) {
            return;
        }
        File file = Jpeg.pick(context.window(), text("image.files"));
        if (file == null) {
            return;
        }
        String caption = input.getText() == null ? "" : input.getText().trim();
        enableCompose(false);
        context.status(text("user.messages.photo_sending"));
        context.load(() -> context.graph().messaging().sendPhoto(context.session(), target.id(),
                Jpeg.encode(file, 1600, Jpeg.CHAT_BYTES), caption, null), sent -> {
            input.clear();
            enableCompose(true);
            context.status("");
            pendingPlaceholder = null;
            loadThread(false);
            loadInbox();
        }, code -> {
            enableCompose(true);
            context.status(text(code));
        });
    }

    private void enableCompose(boolean enabled) {
        input.setDisable(!enabled);
        send.setDisable(!enabled);
        photo.setDisable(!enabled);
    }

    private static String money(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private final class ConversationCell extends ListCell<Conversation> {
        private final Label name = Components.label("", "cell-title");
        private final Label preview = Components.label("", "muted");
        private final Label time = Components.label("", "caption");
        private final Label dot = Components.badge("●", Components.Tone.PEACH);
        private final VBox root;

        ConversationCell() {
            preview.setMaxWidth(260);
            HBox top = Components.row(6, name, Components.spacer(), time);
            root = new VBox(2, top, Components.row(6, preview, Components.spacer(), dot));
        }

        @Override
        protected void updateItem(Conversation conversation, boolean empty) {
            super.updateItem(conversation, empty);
            if (empty || conversation == null) {
                setGraphic(null);
                return;
            }
            name.setText(conversation.otherName() == null || conversation.otherName().isBlank()
                    ? text("user.messages.unknown") : conversation.otherName());
            String body = conversation.lastBody() == null ? "" : conversation.lastBody();
            if (body.isBlank()) {
                body = conversation.lastPrice().map(price -> text("user.messages.price", money(price)))
                        .orElse(conversation == pendingPlaceholder ? "" : text("user.messages.photo"));
            }
            if (conversation.lastFromMe()) {
                body = text("user.messages.you", body);
            }
            preview.setText(body);
            time.setText(Components.time(conversation.lastMessageAt(), context.locale()));
            dot.setVisible(conversation.unread());
            setGraphic(root);
        }
    }
}
