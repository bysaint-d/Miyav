package az.miyav.desktop;

import az.miyav.common.error.AppException;
import az.miyav.common.locale.LocaleCode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class LoginController {
    private final MiyavDesktopApp app;
    @FXML private Label title;
    @FXML private TextField fullName;
    @FXML private TextField email;
    @FXML private PasswordField password;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Button resetButton;
    @FXML private Label message;
    @FXML private Button az;
    @FXML private Button en;
    @FXML private Button ru;

    public LoginController(MiyavDesktopApp app) {
        this.app = app;
    }

    @FXML
    private void initialize() {
        var catalog = app.graph().catalog();
        title.setText(catalog.text("app.name"));
        fullName.setPromptText(catalog.text("auth.full_name"));
        email.setPromptText(catalog.text("auth.email"));
        password.setPromptText(catalog.text("auth.password"));
        loginButton.setText(catalog.text("auth.login"));
        registerButton.setText(catalog.text("auth.register"));
        resetButton.setText(catalog.text("auth.reset"));
        az.setText(catalog.text("language.az"));
        en.setText(catalog.text("language.en"));
        ru.setText(catalog.text("language.ru"));
    }

    @FXML
    private void onLogin() {
        runAuth(() -> app.graph().auth().login(email.getText(), password.getText()), true);
    }

    @FXML
    private void onRegister() {
        runAuth(() -> app.graph().auth().register(email.getText(), password.getText(), fullName.getText()), true);
    }

    @FXML
    private void onReset() {
        runAuth(() -> app.graph().auth().requestPasswordReset(email.getText()), false);
    }

    @FXML
    private void useAz() {
        app.selectLocale(LocaleCode.AZ);
    }

    @FXML
    private void useEn() {
        app.selectLocale(LocaleCode.EN);
    }

    @FXML
    private void useRu() {
        app.selectLocale(LocaleCode.RU);
    }

    private void runAuth(Runnable action, boolean openHome) {
        setBusy(true);
        message.setText("");
        Thread worker = new Thread(() -> {
            try {
                action.run();
                Platform.runLater(() -> {
                    if (openHome) {
                        app.showHome();
                    } else {
                        message.setText(app.graph().catalog().text("auth.reset_sent"));
                        setBusy(false);
                    }
                });
            } catch (AppException exception) {
                Platform.runLater(() -> {
                    message.setText(app.graph().catalog().text(exception.code()));
                    setBusy(false);
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
        resetButton.setDisable(busy);
    }
}
