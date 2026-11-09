package az.miyav.desktop.admin;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The console's building blocks. Styling lives in miyav.css; this class only assembles nodes. */
public final class Components {
    public static final ZoneId BAKU = ZoneId.of("Asia/Baku");

    /** Badge colours from the design system. */
    public enum Tone {
        MINT(""), SAGE("badge-sage"), PEACH("badge-peach"), WARNING("badge-warning"), DANGER("badge-danger"), NEUTRAL("badge-neutral");

        private final String styleClass;

        Tone(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    private Components() {
    }

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Label wrapped(String text, String... styleClasses) {
        Label label = label(text, styleClasses);
        label.setWrapText(true);
        return label;
    }

    public static VBox card(Node... children) {
        VBox card = new VBox(12, children);
        card.getStyleClass().add("card");
        return card;
    }

    public static VBox card(String title, Node... children) {
        VBox card = card(children);
        card.getChildren().add(0, label(title, "card-title"));
        return card;
    }

    /** A headline number with its label and a short hint underneath. */
    public static VBox stat(String glyph, String title, String value, String hint) {
        Label icon = label(glyph, "nav-icon");
        StackPane bubble = new StackPane(icon);
        bubble.getStyleClass().add("avatar");
        bubble.setMinSize(40, 40);
        bubble.setMaxSize(40, 40);
        VBox card = new VBox(6, bubble, label(value, "stat-value"), label(title, "stat-label"), label(hint, "caption"));
        card.getStyleClass().add("card");
        card.setMinWidth(170);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** Lays stat cards out in equal columns, wrapping into rows instead of scrolling sideways. */
    public static void fillGrid(javafx.scene.layout.GridPane grid, java.util.List<? extends Node> cards, int columns) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.setHgap(16);
        grid.setVgap(16);
        for (int column = 0; column < columns; column++) {
            javafx.scene.layout.ColumnConstraints constraint = new javafx.scene.layout.ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            grid.getColumnConstraints().add(constraint);
        }
        for (int index = 0; index < cards.size(); index++) {
            grid.add(cards.get(index), index % columns, index / columns);
        }
    }

    public static Label badge(String text, Tone tone) {
        Label badge = label(text, "badge");
        if (!tone.styleClass.isEmpty()) {
            badge.getStyleClass().add(tone.styleClass);
        }
        return badge;
    }

    public static StackPane avatar(String initials, double size) {
        Label text = label(initials);
        text.setStyle("-fx-font-size: " + Math.round(size * 0.38) + "px; -fx-font-weight: 700; -fx-text-fill: #315B49;");
        StackPane avatar = new StackPane(text);
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    public static VBox empty(String glyph, String title, String body) {
        Label icon = label(glyph, "empty-glyph");
        Label heading = label(title, "card-title");
        Label text = wrapped(body, "muted");
        text.setMaxWidth(420);
        text.setStyle("-fx-text-alignment: center;");
        VBox empty = new VBox(8, icon, heading, text);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(36));
        return empty;
    }

    public static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static HBox row(double spacing, Node... children) {
        HBox row = new HBox(spacing, children);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    public static Image image(byte[] bytes, double width, double height) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        Image image = new Image(new ByteArrayInputStream(bytes), width, height, true, true);
        return image.isError() ? null : image;
    }

    /** Fills width × height with the image's centre, cropping the overflow instead of stretching it. */
    public static ImageView cover(Image image, double width, double height) {
        ImageView view = new ImageView(image);
        double scale = Math.max(width / image.getWidth(), height / image.getHeight());
        double shownWidth = width / scale;
        double shownHeight = height / scale;
        view.setViewport(new Rectangle2D((image.getWidth() - shownWidth) / 2, (image.getHeight() - shownHeight) / 2,
                shownWidth, shownHeight));
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setSmooth(true);
        return view;
    }

    /** The whole image inside width × height, keeping its proportions. */
    public static ImageView contain(Image image, double width, double height) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setSmooth(true);
        return view;
    }

    public static String date(Instant instant, Locale locale) {
        return instant == null ? "—" : DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(BAKU).format(instant);
    }

    public static String dateTime(Instant instant, Locale locale) {
        return instant == null ? "—" : DateTimeFormatter.ofPattern("d MMM, HH:mm", locale).withZone(BAKU).format(instant);
    }

    public static String time(Instant instant, Locale locale) {
        return instant == null ? "—" : DateTimeFormatter.ofPattern("HH:mm", locale).withZone(BAKU).format(instant);
    }

    /** Compact age of an event: minutes, hours or days. */
    public static String ago(Instant then, Instant now, ConsoleContext context) {
        if (then == null) {
            return "—";
        }
        Duration age = Duration.between(then, now);
        if (age.toMinutes() < 60) {
            return context.text("console.ago_minutes", Math.max(1, age.toMinutes()));
        }
        if (age.toHours() < 24) {
            return context.text("console.ago_hours", age.toHours());
        }
        return context.text("console.ago_days", age.toDays());
    }

    public static String speciesGlyph(String species) {
        return switch (species == null ? "" : species) {
            case "dog" -> "🐕";
            case "cat" -> "🐈";
            case "bird" -> "🐦";
            default -> "🐾";
        };
    }
}
