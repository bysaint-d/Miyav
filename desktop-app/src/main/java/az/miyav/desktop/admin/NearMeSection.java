package az.miyav.desktop.admin;

import az.miyav.domain.admin.UserSummary;
import az.miyav.domain.map.MapPoint;
import az.miyav.domain.map.MapPointKind;
import az.miyav.domain.map.WalkHelperPoint;
import az.miyav.domain.walk.WalkActivity;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NearMe: people walking right now, people offering walks and adoption pins, on the same OpenStreetMap
 * map the mobile apps use. Only voluntarily shared positions are drawn, each with its privacy circle.
 */
public final class NearMeSection extends AdminSection {
    private final WebView web = new WebView();
    private final VBox preview = new VBox(12);
    private final Map<MapPointKind, CheckBox> layers = new EnumMap<>(MapPointKind.class);
    private final Map<String, MapPoint> points = new HashMap<>();
    private final Map<UUID, WalkActivity> walks = new HashMap<>();
    private final Bridge bridge = new Bridge();
    private boolean pageReady;
    private List<MapPoint> pending = List.of();

    public NearMeSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "nearme";
    }

    @Override
    public String icon() {
        return "📍";
    }

    @Override
    public String titleKey() {
        return "console.nearme";
    }

    @Override
    public String subtitleKey() {
        return "console.nearme_subtitle";
    }

    @Override
    protected Node build() {
        WebEngine engine = web.getEngine();
        engine.getLoadWorker().stateProperty().addListener((observable, before, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("miyav", bridge);
                pageReady = true;
                draw(pending);
            }
        });
        engine.loadContent(page());
        HBox legend = Components.row(18,
                layer(MapPointKind.LIVE_WALK, "console.layer_walks", "#315B49"),
                layer(MapPointKind.WALK_HELPER, "console.layer_helpers", "#7FB8A4"),
                layer(MapPointKind.ADOPTION, "console.layer_adoption", "#E89A6B"),
                Components.spacer(),
                Components.label(text("console.privacy_note"), "caption"));
        StackPane frame = new StackPane(web);
        frame.getStyleClass().add("card");
        frame.setStyle("-fx-padding: 0; -fx-background-radius: 22;");
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(44);
        clip.setArcHeight(44);
        clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty());
        frame.setClip(clip);
        frame.setPrefHeight(600);
        HBox.setHgrow(frame, Priority.ALWAYS);
        preview.getStyleClass().add("card");
        preview.setPrefWidth(320);
        preview.setMinWidth(300);
        showPreview(null);
        return new VBox(14, legend, new HBox(16, frame, preview));
    }

    private CheckBox layer(MapPointKind kind, String key, String color) {
        CheckBox box = new CheckBox(text(key));
        box.setSelected(true);
        box.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: 700;");
        box.setOnAction(event -> refresh());
        layers.put(kind, box);
        return box;
    }

    @Override
    public void refresh() {
        Set<MapPointKind> kinds = EnumSet.noneOf(MapPointKind.class);
        layers.forEach((kind, box) -> {
            if (box.isSelected()) {
                kinds.add(kind);
            }
        });
        context.members(members -> {
        });
        context.load(() -> context.insights().walkActivity(context.session()), list -> {
            walks.clear();
            for (WalkActivity walk : list) {
                walks.put(walk.id(), walk);
            }
        });
        context.load(() -> context.insights().mapPoints(context.session(), kinds), list -> {
            points.clear();
            for (MapPoint point : list) {
                points.put(point.id().toString(), point);
            }
            pending = list;
            draw(list);
        });
    }

    private void draw(List<MapPoint> list) {
        if (!pageReady) {
            return;
        }
        StringBuilder json = new StringBuilder("[");
        for (MapPoint point : list) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"id\":").append(quote(point.id().toString()))
                    .append(",\"kind\":").append(quote(point.kind().wireValue()))
                    .append(",\"title\":").append(quote(point.title()))
                    .append(",\"subtitle\":").append(quote(point.subtitle()))
                    .append(",\"lat\":").append(point.position().latitude())
                    .append(",\"lng\":").append(point.position().longitude())
                    .append(",\"radius\":").append(point.privacyRadiusMeters())
                    .append('}');
        }
        json.append(']');
        web.getEngine().executeScript("setPoints(" + json + ", " + quote(text("console.map_empty")) + ")");
    }

    private void showPreview(MapPoint point) {
        preview.getChildren().clear();
        if (point == null) {
            preview.setAlignment(Pos.CENTER);
            preview.getChildren().add(Components.empty("📍", text("console.map_select_title"), text("console.map_select_body")));
            return;
        }
        preview.setAlignment(Pos.TOP_LEFT);
        Components.Tone tone = switch (point.kind()) {
            case LIVE_WALK -> Components.Tone.MINT;
            case WALK_HELPER -> Components.Tone.SAGE;
            case ADOPTION -> Components.Tone.PEACH;
        };
        preview.getChildren().addAll(
                Components.badge(text("console.kind_" + point.kind().wireValue()), tone),
                Components.row(12, Components.avatar(initials(point.title()), 52),
                        new VBox(2, Components.label(point.title(), "card-title"), Components.label(point.subtitle(), "caption"))),
                Components.wrapped(text("console.map_precision", point.privacyRadiusMeters() >= 1000
                        ? text("console.about_km", point.privacyRadiusMeters() / 1000)
                        : text("console.about_m", point.privacyRadiusMeters())), "muted"));
        if (point instanceof WalkHelperPoint helper && helper.verified()) {
            preview.getChildren().add(Components.badge(text("console.verified_helper"), Components.Tone.MINT));
        }
        UUID memberId = memberOf(point);
        if (memberId != null) {
            context.members(members -> {
                UserSummary member = members.get(memberId);
                if (member != null) {
                    preview.getChildren().add(Components.wrapped(text("console.member_counts", member.pets(), member.walks(),
                            member.listings()), "muted"));
                }
            });
            Button open = new Button(text("console.open_profile"));
            open.getStyleClass().add("primary");
            open.setMaxWidth(Double.MAX_VALUE);
            open.setOnAction(event -> context.open("users", memberId));
            preview.getChildren().add(open);
        } else if (point.kind() == MapPointKind.ADOPTION) {
            Button open = new Button(text("console.open_listing"));
            open.getStyleClass().add("primary");
            open.setMaxWidth(Double.MAX_VALUE);
            open.setOnAction(event -> context.open("adoption", point.id()));
            preview.getChildren().add(open);
        }
    }

    /** Helpers are placed by their own profile id; live walks by their walk, which belongs to a member. */
    private UUID memberOf(MapPoint point) {
        return switch (point.kind()) {
            case WALK_HELPER -> point.id();
            case LIVE_WALK -> walks.containsKey(point.id()) ? walks.get(point.id()).userId() : null;
            case ADOPTION -> null;
        };
    }

    private static String initials(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : (value == null ? "" : value).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '<' -> out.append("\\u003c");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String page() {
        try (InputStream input = NearMeSection.class.getResourceAsStream("/az/miyav/desktop/nearme-map.html")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException exception) {
            return "<p>Map unavailable</p>";
        }
    }

    /** Called from the map page when a marker is clicked. Must stay public for the JavaScript bridge. */
    public final class Bridge {
        public void select(String id) {
            javafx.application.Platform.runLater(() -> showPreview(points.get(id)));
        }
    }
}
