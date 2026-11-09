package az.miyav.desktop.user;

import az.miyav.desktop.admin.Components;
import az.miyav.domain.notification.Notification;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** The member's notifications: newest first, tap to open the chat they are about. */
public final class NotificationsPage extends UserPage {
    private final VBox list = new VBox(10);
    private List<Notification> items = List.of();

    public NotificationsPage(UserContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "notifications";
    }

    @Override
    public String titleKey() {
        return "user.nav.notifications";
    }

    @Override
    public String icon() {
        return "🔔";
    }

    @Override
    protected Node build() {
        Button readAll = new Button(text("user.notifications.read_all"));
        readAll.getStyleClass().add("ghost");
        readAll.setOnAction(event -> readAll());
        Button clear = new Button(text("user.notifications.clear"));
        clear.getStyleClass().add("ghost");
        clear.setOnAction(event -> context.load(() -> {
            context.graph().notifications().deleteAll(context.session());
            return Boolean.TRUE;
        }, done -> refresh()));
        HBox actions = Components.row(8, Components.spacer(), readAll, clear);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        return new VBox(12, actions, scroll);
    }

    @Override
    public void refresh() {
        context.load(() -> context.graph().notifications().list(context.session()), loaded -> {
            items = loaded;
            render();
            context.badgesChanged();
        });
    }

    private void render() {
        if (items.isEmpty()) {
            list.getChildren().setAll(Components.empty("🔔", text("user.notifications.empty"), ""));
            return;
        }
        list.getChildren().clear();
        for (Notification notification : items) {
            list.getChildren().add(card(notification));
        }
    }

    private Node card(Notification notification) {
        Label title = Components.label(notification.title(), "cell-title");
        Label time = Components.label(Components.dateTime(notification.createdAt(), context.locale()), "caption");
        HBox header = Components.row(8, title, Components.spacer(), time);
        if (notification.unread()) {
            header.getChildren().add(1, Components.badge(text("user.notifications.new"), Components.Tone.PEACH));
        }
        Button remove = new Button("×");
        remove.getStyleClass().add("ghost");
        remove.setOnAction(event -> {
            event.consume();
            context.load(() -> {
                context.graph().notifications().delete(context.session(), notification.id());
                return Boolean.TRUE;
            }, done -> refresh());
        });
        header.getChildren().add(remove);
        VBox card = Components.card(header, Components.wrapped(notification.body(), "muted"));
        if (notification.unread()) {
            card.getStyleClass().add("card-mint");
        }
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> open(notification));
        return card;
    }

    private void open(Notification notification) {
        if (notification.unread()) {
            context.load(() -> {
                context.graph().notifications().markRead(context.session(), notification.id());
                return Boolean.TRUE;
            }, done -> context.badgesChanged(), code -> {
            });
        }
        notification.conversationId().ifPresentOrElse(room -> context.open("messages", room), this::refresh);
    }

    private void readAll() {
        List<Notification> unread = items.stream().filter(Notification::unread).toList();
        context.load(() -> {
            for (Notification notification : unread) {
                context.graph().notifications().markRead(context.session(), notification.id());
            }
            return Boolean.TRUE;
        }, done -> refresh());
    }
}
