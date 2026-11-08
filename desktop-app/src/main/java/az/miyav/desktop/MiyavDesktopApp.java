package az.miyav.desktop;

import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.auth.AuthSession;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

public final class MiyavDesktopApp extends Application {
    private Stage stage;
    private DesktopGraph graph;
    private String screen = "login.fxml";

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        this.graph = DesktopGraph.load();
        stage.setTitle("MIYAV");
        stage.setMinWidth(880);
        stage.setMinHeight(640);
        Optional<AuthSession> restored = graph.auth().restore();
        if (restored.isPresent()) {
            showHome();
        } else {
            showLogin();
        }
        stage.show();
    }

    public DesktopGraph graph() {
        return graph;
    }

    public void showLogin() {
        show("login.fxml");
    }

    public void showHome() {
        show("home.fxml");
    }

    public void selectLocale(LocaleCode locale) {
        graph.selectLocale(locale);
        show(screen);
    }

    private void show(String fxml) {
        screen = fxml;
        FXMLLoader loader = new FXMLLoader(MiyavDesktopApp.class.getResource(fxml));
        loader.setControllerFactory(type -> {
            if (type == LoginController.class) {
                return new LoginController(this);
            }
            if (type == HomeController.class) {
                return new HomeController(this);
            }
            throw new IllegalArgumentException(type.getName());
        });
        try {
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 720);
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
