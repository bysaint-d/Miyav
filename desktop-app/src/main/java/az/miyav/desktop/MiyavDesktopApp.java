package az.miyav.desktop;

import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.security.AccessPolicy;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Opens straight into public Discover, so guests can browse. A restored administrator session goes on to
 * the admin console; the console is reachable only through the access policy and RLS on the server.
 */
public final class MiyavDesktopApp extends Application {
    /** Where the object graph comes from; the console preview test supplies one without a network. */
    static Supplier<DesktopGraph> graphFactory = DesktopGraph::load;
    private Stage stage;
    private DesktopGraph graph;
    private String screen = "discover.fxml";
    private DiscoverController discover;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.graph = graphFactory.get();
        stage.setTitle("MIYAV");
        var icon = MiyavDesktopApp.class.getResourceAsStream("miyav-icon.png");
        if (icon != null) {
            stage.getIcons().add(new Image(icon));
        }
        stage.setMinWidth(980);
        stage.setMinHeight(660);
        graph.session().prime();
        showDiscover();
        stage.show();
        Thread restore = new Thread(() -> {
            graph.session().restore();
            Platform.runLater(() -> {
                if (!"discover.fxml".equals(screen)) {
                    return;
                }
                if (graph.session().decide(ProtectedAction.ADMIN) == AccessPolicy.Decision.ALLOWED) {
                    show("home.fxml");
                } else if (discover != null) {
                    discover.onAuthChanged();
                }
            });
        }, "miyav-restore");
        restore.setDaemon(true);
        restore.start();
    }

    public DesktopGraph graph() {
        return graph;
    }

    public void showDiscover() {
        show("discover.fxml");
    }

    /** Admin dashboard; guests are asked to sign in and non-admin accounts stay on Discover. */
    public void showAdmin() {
        AccessPolicy.Decision decision = graph.session().request(ProtectedAction.ADMIN);
        if (decision == AccessPolicy.Decision.SIGN_IN_REQUIRED
                && AuthDialog.show(stage, this, ProtectedAction.ADMIN)) {
            graph.session().takePending();
            decision = graph.session().decide(ProtectedAction.ADMIN);
        }
        if (decision == AccessPolicy.Decision.ALLOWED) {
            show("home.fxml");
        } else if (discover != null) {
            discover.onAuthChanged();
        }
    }

    public void selectLocale(LocaleCode locale) {
        graph.selectLocale(locale);
        show(screen);
    }

    private void show(String fxml) {
        screen = fxml;
        FXMLLoader loader = new FXMLLoader(MiyavDesktopApp.class.getResource(fxml));
        loader.setControllerFactory(type -> {
            if (type == DiscoverController.class) {
                discover = new DiscoverController(this);
                return discover;
            }
            if (type == HomeController.class) {
                return new HomeController(this);
            }
            throw new IllegalArgumentException(type.getName());
        });
        try {
            Parent root = loader.load();
            Scene scene = new Scene(root, 1360, 860);
            scene.getStylesheets().add(MiyavDesktopApp.class.getResource("miyav.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open " + fxml, exception);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
