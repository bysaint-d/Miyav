package az.miyav.desktop;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * A listing's photos in their own resizable window, each shown whole and as large as the window allows.
 * Arrow keys or the side buttons switch photos, double-click toggles full screen, Esc closes.
 */
public final class PhotoViewer {
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "miyav-photo-viewer");
        thread.setDaemon(true);
        return thread;
    });
    private static final String BUTTON = "-fx-background-color: rgba(255,255,255,0.16); -fx-text-fill: white;"
            + " -fx-font-size: 28px; -fx-background-radius: 28; -fx-min-width: 56; -fx-min-height: 56; -fx-cursor: hand;";
    private final List<String> paths;
    private final Function<String, byte[]> source;
    private final Map<String, Image> loaded = new HashMap<>();
    private final ImageView view = new ImageView();
    private final Label counter = new Label();
    private final Label status = new Label("…");
    private int index;

    private PhotoViewer(List<String> paths, int start, Function<String, byte[]> source) {
        this.paths = List.copyOf(paths);
        this.index = Math.max(0, Math.min(start, paths.size() - 1));
        this.source = source;
    }

    /** {@code source} fetches a photo's bytes and runs off the JavaFX thread. */
    public static void open(Window owner, String title, List<String> paths, int start, Function<String, byte[]> source) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        new PhotoViewer(paths, start, source).show(owner, title);
    }

    private void show(Window owner, String title) {
        view.setPreserveRatio(true);
        view.setSmooth(true);
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 18px;");
        counter.setStyle("-fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.55); -fx-padding: 4 12;"
                + " -fx-background-radius: 12; -fx-font-weight: bold;");
        Button previous = new Button("‹");
        Button next = new Button("›");
        previous.setStyle(BUTTON);
        next.setStyle(BUTTON);
        previous.setOnAction(event -> step(-1));
        next.setOnAction(event -> step(1));
        boolean several = paths.size() > 1;
        for (javafx.scene.Node node : List.of(previous, next, counter)) {
            node.setVisible(several);
            node.setManaged(several);
        }
        StackPane root = new StackPane(status, view, previous, next, counter);
        root.setStyle("-fx-background-color: #111;");
        StackPane.setAlignment(previous, Pos.CENTER_LEFT);
        StackPane.setAlignment(next, Pos.CENTER_RIGHT);
        StackPane.setAlignment(counter, Pos.BOTTOM_CENTER);
        StackPane.setMargin(previous, new Insets(0, 0, 0, 16));
        StackPane.setMargin(next, new Insets(0, 16, 0, 0));
        StackPane.setMargin(counter, new Insets(0, 0, 18, 0));
        view.fitWidthProperty().bind(root.widthProperty().subtract(several ? 160 : 32));
        view.fitHeightProperty().bind(root.heightProperty().subtract(several ? 70 : 32));

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, screen.getWidth() * 0.8, screen.getHeight() * 0.85);
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(title);
        stage.setScene(scene);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && !stage.isFullScreen()) {
                stage.close();
            } else if (event.getCode() == KeyCode.LEFT) {
                step(-1);
            } else if (event.getCode() == KeyCode.RIGHT) {
                step(1);
            }
        });
        view.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                stage.setFullScreen(!stage.isFullScreen());
            }
        });
        render();
        stage.show();
    }

    private void step(int delta) {
        index = Math.floorMod(index + delta, paths.size());
        render();
    }

    private void render() {
        counter.setText((index + 1) + " / " + paths.size());
        String path = paths.get(index);
        Image cached = loaded.get(path);
        if (cached != null) {
            view.setImage(cached);
            status.setVisible(false);
            return;
        }
        view.setImage(null);
        status.setVisible(true);
        LOADER.execute(() -> {
            Image image;
            try {
                byte[] bytes = source.apply(path);
                image = bytes == null || bytes.length == 0 ? null : new Image(new ByteArrayInputStream(bytes));
            } catch (RuntimeException exception) {
                image = null;
            }
            Image result = image == null || image.isError() ? null : image;
            Platform.runLater(() -> {
                if (result != null) {
                    loaded.put(path, result);
                }
                if (path.equals(paths.get(index))) {
                    view.setImage(result);
                    status.setVisible(result == null);
                    status.setText(result == null ? "⚠" : "…");
                }
            });
        });
    }
}
