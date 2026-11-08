package az.miyav.desktop.admin;

import az.miyav.domain.admin.Broadcast;
import az.miyav.domain.admin.BroadcastAudience;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Push notifications: totals, a form to send a notification to everyone, owners or walkers (optionally in
 * one city) with a live phone preview and reach count, and the history of sent notifications.
 */
public final class NotificationsSection extends AdminSection {
    private static final String ALL_CITIES = "";
    private static final List<String> CITIES = List.of("Bakı", "Sumqayıt", "Abşeron");
    private final HBox stats = new HBox(16);
    private final TextField title = new TextField();
    private final TextArea body = new TextArea();
    private final ComboBox<BroadcastAudience> audience = new ComboBox<>();
    private final ComboBox<String> city = new ComboBox<>();
    private final javafx.scene.control.Label reach = Components.label("", "muted");
    private final javafx.scene.control.Label counter = Components.label("", "muted");
    private final javafx.scene.control.Label previewTitle = Components.label("", "card-title");
    private final javafx.scene.control.Label previewBody = Components.wrapped("", "muted");
    private final Button send = new Button();
    private final VBox history = new VBox(10);
    private int reachable;

    public NotificationsSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "notifications";
    }

    @Override
    public String icon() {
        return "🔔";
    }

    @Override
    public String titleKey() {
        return "console.notifications";
    }

    @Override
    public String subtitleKey() {
        return "console.notifications_subtitle";
    }

    @Override
    protected Node build() {
        title.setPromptText(text("console.broadcast_heading"));
        body.setPromptText(text("console.broadcast_body"));
        body.setWrapText(true);
        body.setPrefRowCount(4);
        title.textProperty().addListener((obs, old, value) -> {
            if (value.length() > Broadcast.MAX_TITLE) {
                title.setText(value.substring(0, Broadcast.MAX_TITLE));
            }
            renderForm();
        });
        body.textProperty().addListener((obs, old, value) -> {
            if (value.length() > Broadcast.MAX_BODY) {
                body.setText(value.substring(0, Broadcast.MAX_BODY));
            }
            renderForm();
        });
        audience.getItems().setAll(BroadcastAudience.values());
        audience.setConverter(converter(value -> value == null ? "" : text(switch (value) {
            case ALL -> "console.broadcast_audience_all";
            case OWNERS -> "console.broadcast_audience_owners";
            case SITTERS -> "console.broadcast_audience_sitters";
        })));
        audience.setValue(BroadcastAudience.ALL);
        List<String> cities = new ArrayList<>();
        cities.add(ALL_CITIES);
        cities.addAll(CITIES);
        city.getItems().setAll(cities);
        city.setConverter(converter(value -> value == null || value.isEmpty() ? text("console.broadcast_city_all") : value));
        city.setValue(ALL_CITIES);
        audience.valueProperty().addListener((obs, old, value) -> updateReach());
        city.valueProperty().addListener((obs, old, value) -> updateReach());
        send.setText(text("console.broadcast_send"));
        send.getStyleClass().add("primary");
        send.setMaxWidth(Double.MAX_VALUE);
        send.setOnAction(event -> confirmAndSend());

        VBox form = new VBox(10,
                Components.wrapped(text("console.broadcast_hint"), "muted"),
                Components.label(text("console.broadcast_heading"), "muted"), title,
                Components.label(text("console.broadcast_body"), "muted"), body, counter,
                Components.row(12, labelled("console.broadcast_audience", audience), labelled("console.broadcast_city", city)),
                reach, send);
        VBox compose = Components.card(text("console.broadcast_title"), form);
        HBox.setHgrow(compose, Priority.ALWAYS);

        VBox phone = new VBox(6, Components.label("🐾 MIYAV", "muted"), previewTitle, previewBody);
        phone.getStyleClass().add("card");
        phone.setMinWidth(260);
        phone.setMaxWidth(300);
        VBox preview = Components.card(text("console.broadcast_preview"), phone);
        preview.getStyleClass().add("card-mint");

        VBox log = Components.card(text("console.broadcast_history"), history);
        renderForm();
        return new VBox(18, stats, new HBox(18, compose, preview), log);
    }

    private static VBox labelled(String key, ComboBox<?> box) {
        box.setMaxWidth(Double.MAX_VALUE);
        VBox column = new VBox(4, box);
        HBox.setHgrow(column, Priority.ALWAYS);
        return column;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return label.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    private void renderForm() {
        String heading = title.getText().trim();
        String text = body.getText().trim();
        previewTitle.setText(heading.isEmpty() ? text("console.broadcast_heading") : heading);
        previewBody.setText(text.isEmpty() ? text("console.broadcast_body") : text);
        counter.setText(body.getText().length() + " / " + Broadcast.MAX_BODY);
        send.setDisable(heading.isEmpty() || text.isEmpty() || reachable == 0);
    }

    private String selectedCity() {
        String value = city.getValue();
        return value == null || value.isEmpty() ? null : value;
    }

    private void updateReach() {
        BroadcastAudience target = audience.getValue();
        String place = selectedCity();
        context.load(() -> context.graph().broadcasts().audienceSize(context.session(), target, place), count -> {
            reachable = count;
            reach.setText(text("console.broadcast_reach", count));
            renderForm();
        });
    }

    private void confirmAndSend() {
        String heading = title.getText().trim();
        String text = body.getText().trim();
        BroadcastAudience target = audience.getValue();
        String place = selectedCity();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, text("console.broadcast_confirm", heading, reachable),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(context.window());
        confirm.setHeaderText(text("console.broadcast_title"));
        confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(ok -> {
            send.setDisable(true);
            context.load(() -> context.graph().broadcasts().send(context.session(), heading, text, target, place), count -> {
                context.status(text("console.broadcast_sent", count));
                title.clear();
                body.clear();
                refresh();
            }, code -> {
                renderForm();
                Alert failed = new Alert(Alert.AlertType.ERROR, text(code), ButtonType.OK);
                failed.initOwner(context.window());
                failed.showAndWait();
            });
        });
    }

    private void renderHistory(List<Broadcast> sent) {
        history.getChildren().clear();
        if (sent.isEmpty()) {
            history.getChildren().add(Components.label(text("console.broadcast_history_empty"), "muted"));
            return;
        }
        for (Broadcast broadcast : sent) {
            String who = text(switch (broadcast.audience()) {
                case ALL -> "console.broadcast_audience_all";
                case OWNERS -> "console.broadcast_audience_owners";
                case SITTERS -> "console.broadcast_audience_sitters";
            }) + broadcast.city().map(place -> " · " + place).orElse("");
            VBox row = new VBox(2,
                    Components.label(broadcast.title(), "card-title"),
                    Components.wrapped(broadcast.body(), "muted"),
                    Components.label(text("console.broadcast_row", Components.dateTime(broadcast.sentAt(), context.locale()),
                            who, broadcast.recipients(), broadcast.senderName()), "caption"));
            history.getChildren().add(row);
        }
    }

    @Override
    public void refresh() {
        context.load(() -> context.insights().overview(context.session()), overview -> {
            int total = overview.counts().notifications();
            int unread = overview.counts().unreadNotifications();
            int readShare = total == 0 ? 0 : Math.round(100f * (total - unread) / total);
            stats.getChildren().setAll(
                    Components.stat("🔔", text("console.stat_notifications"), String.valueOf(total), text("console.stat_notifications_hint")),
                    Components.stat("●", text("console.stat_unread"), String.valueOf(unread), text("console.stat_unread_hint")),
                    Components.stat("✓", text("console.stat_read_share"), readShare + "%", text("console.stat_read_share_hint")));
        });
        context.load(() -> context.graph().broadcasts().recent(context.session()), this::renderHistory);
        updateReach();
    }
}
