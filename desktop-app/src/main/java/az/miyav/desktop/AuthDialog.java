package az.miyav.desktop;

import az.miyav.common.error.AppException;
import az.miyav.domain.auth.ProtectedAction;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.function.Supplier;

/**
 * The MIYAV sign-in dialog, opened only when a guest reaches an account-only action. Closing it keeps
 * the user browsing as a guest; signing in returns control to the caller, which resumes the action.
 */
final class AuthDialog {
    private final MiyavDesktopApp app;
    private final DesktopGraph graph;
    private final Stage stage = new Stage();
    private final VBox choices = new VBox(10);
    private final VBox form = new VBox(10);
    private final TextField fullName = new TextField();
    private final TextField email = new TextField();
    private final PasswordField password = new PasswordField();
    private final Button submit = new Button();
    private final Label status = new Label();
    private final javafx.scene.control.CheckBox terms = new javafx.scene.control.CheckBox();
    private final VBox termsRow = new VBox(2);
    private DesktopGoogleSignIn google;
    private boolean register;
    private boolean signedIn;

    private AuthDialog(Window owner, MiyavDesktopApp app, ProtectedAction action) {
        this.app = app;
        this.graph = app.graph();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.UTILITY);
        stage.setTitle(graph.text("guest.sheet.title"));
        stage.setResizable(false);

        Label wordmark = new Label("MIYAV.");
        wordmark.getStyleClass().add("wordmark");
        Label title = new Label(graph.text("guest.sheet.title"));
        title.getStyleClass().add("sheet-title");
        Label prompt = new Label(graph.text(action == null ? "auth.required" : action.promptKey()));
        prompt.setWrapText(true);
        prompt.getStyleClass().add("muted");

        Button googleButton = wide(graph.text("guest.google"), "ghost");
        googleButton.setOnAction(event -> google());
        Button emailButton = wide(graph.text("guest.email"), "primary");
        emailButton.setOnAction(event -> openForm(false));
        Button telegramButton = wide(graph.text("guest.telegram"), "ghost");
        telegramButton.setOnAction(event -> {
            if (!graph.telegram().available()) {
                status.setText(graph.text("guest.telegram_unavailable"));
            }
        });
        Button registerButton = wide(graph.text("guest.register"), "ghost");
        registerButton.setOnAction(event -> openForm(true));
        Hyperlink later = new Hyperlink(graph.text("guest.later"));
        later.getStyleClass().add("link");
        later.setOnAction(event -> stage.close());
        choices.getChildren().addAll(googleButton, emailButton);
        if (graph.telegram().available()) {
            choices.getChildren().add(telegramButton);
        }
        choices.getChildren().addAll(registerButton, later);
        choices.setAlignment(Pos.CENTER);

        fullName.setPromptText(graph.text("auth.full_name"));
        email.setPromptText(graph.text("auth.email"));
        password.setPromptText(graph.text("auth.password"));
        submit.getStyleClass().add("primary");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.setOnAction(event -> submit());
        Hyperlink reset = new Hyperlink(graph.text("auth.reset"));
        reset.getStyleClass().add("link");
        reset.setOnAction(event -> run(() -> {
            graph.auth().requestPasswordReset(email.getText());
            return false;
        }, "auth.reset_sent"));
        Hyperlink back = new Hyperlink("← " + graph.text("guest.sheet.title"));
        back.getStyleClass().add("link");
        back.setOnAction(event -> showChoices());
        terms.setText(graph.text("auth.terms_agree"));
        terms.setWrapText(true);
        terms.selectedProperty().addListener((observable, before, checked) -> {
            if (checked) {
                status.setText("");
            }
        });
        Hyperlink readTerms = new Hyperlink(graph.text("legal.terms"));
        readTerms.getStyleClass().add("link");
        readTerms.setOnAction(event -> legal("legal.terms", "legal.terms_body"));
        Hyperlink readPrivacy = new Hyperlink(graph.text("legal.privacy"));
        readPrivacy.getStyleClass().add("link");
        readPrivacy.setOnAction(event -> legal("legal.privacy", "legal.privacy_body"));
        termsRow.getChildren().addAll(terms, new javafx.scene.layout.HBox(4, readTerms, readPrivacy));
        form.getChildren().addAll(fullName, email, password, termsRow, submit, reset, back);
        form.setAlignment(Pos.CENTER);

        status.setWrapText(true);
        status.getStyleClass().add("muted");

        VBox root = new VBox(14, wordmark, title, prompt, choices, form, status);
        root.getStyleClass().addAll("screen", "sheet");
        root.setPadding(new Insets(28));
        root.setPrefWidth(420);
        showChoices();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(MiyavDesktopApp.class.getResource("miyav.css").toExternalForm());
        stage.setScene(scene);
        stage.setOnHidden(event -> {
            if (google != null) {
                google.cancel();
            }
        });
    }

    /** Opens the dialog and waits. Returns true when the user is signed in afterwards. */
    static boolean show(Window owner, MiyavDesktopApp app, ProtectedAction action) {
        AuthDialog dialog = new AuthDialog(owner, app, action);
        app.graph().session().beginAuthentication();
        dialog.stage.showAndWait();
        if (!dialog.signedIn) {
            app.graph().session().cancelAuthentication();
        }
        return dialog.signedIn;
    }

    private Button wide(String text, String style) {
        Button button = new Button(text);
        button.getStyleClass().add(style);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMnemonicParsing(false);
        return button;
    }

    private void showChoices() {
        choices.setVisible(true);
        choices.setManaged(true);
        form.setVisible(false);
        form.setManaged(false);
        status.setText("");
        stage.sizeToScene();
    }

    private void openForm(boolean register) {
        this.register = register;
        choices.setVisible(false);
        choices.setManaged(false);
        form.setVisible(true);
        form.setManaged(true);
        fullName.setVisible(register);
        fullName.setManaged(register);
        termsRow.setVisible(register);
        termsRow.setManaged(register);
        submit.setText(graph.text(register ? "auth.register" : "auth.login"));
        status.setText("");
        stage.sizeToScene();
        email.requestFocus();
    }

    private void submit() {
        if (register) {
            if (!terms.isSelected()) {
                status.setText(graph.text("auth.terms_required"));
                return;
            }
            boolean accepted = terms.isSelected();
            run(() -> {
                graph.session().register(email.getText(), password.getText(), fullName.getText(), null, accepted);
                return true;
            }, null);
        } else {
            run(() -> {
                graph.session().login(email.getText(), password.getText());
                return true;
            }, null);
        }
    }

    private void legal(String titleKey, String bodyKey) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(graph.text(titleKey));
        alert.setHeaderText(graph.text(titleKey));
        Label body = new Label(graph.text(bodyKey));
        body.setWrapText(true);
        body.setMaxWidth(460);
        alert.getDialogPane().setContent(body);
        alert.showAndWait();
    }

    private void google() {
        google = new DesktopGoogleSignIn(graph);
        DesktopGoogleSignIn current = google;
        status.setText(graph.text("guest.google_waiting"));
        run(() -> {
            current.run(url -> Platform.runLater(() -> app.getHostServices().showDocument(url)));
            return true;
        }, null);
    }

    /** Runs auth work off the UI thread; closes the dialog when the user ends up signed in. */
    private void run(Supplier<Boolean> work, String successKey) {
        submit.setDisable(true);
        Thread worker = new Thread(() -> {
            try {
                boolean done = work.get();
                Platform.runLater(() -> {
                    submit.setDisable(false);
                    if (done) {
                        signedIn = true;
                        stage.close();
                    } else if (successKey != null) {
                        status.setText(graph.text(successKey));
                    }
                });
            } catch (AppException exception) {
                Platform.runLater(() -> {
                    submit.setDisable(false);
                    status.setText(graph.text(exception.code()));
                });
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    submit.setDisable(false);
                    status.setText(graph.text("error.generic"));
                });
            }
        }, "miyav-auth");
        worker.setDaemon(true);
        worker.start();
    }
}
