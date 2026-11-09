package az.miyav.desktop.user;

import az.miyav.desktop.admin.Components;
import az.miyav.domain.location.Districts;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.User;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The member's own profile: name, city, phone and who may see it, bio, photo and password. */
public final class ProfilePage extends UserPage {
    private final StackPane avatar = new StackPane();
    private final Label email = Components.label("", "muted");
    private final TextField fullName = new TextField();
    private final ComboBox<String> city = new ComboBox<>();
    private final ComboBox<String> district = new ComboBox<>();
    private final TextField phone = new TextField();
    private final CheckBox phonePublic = new CheckBox();
    private final TextArea bio = new TextArea();
    private final Label profileStatus = Components.wrapped("", "muted");
    private final PasswordField currentPassword = new PasswordField();
    private final PasswordField newPassword = new PasswordField();
    private final Label passwordStatus = Components.wrapped("", "muted");
    private VBox passwordCard;
    private User user;

    public ProfilePage(UserContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "profile";
    }

    @Override
    public String titleKey() {
        return "user.nav.profile";
    }

    @Override
    public String icon() {
        return "👤";
    }

    @Override
    protected Node build() {
        avatar.setMinSize(96, 96);
        avatar.setMaxSize(96, 96);
        Button changePhoto = new Button(text("user.profile.avatar"));
        changePhoto.getStyleClass().add("ghost");
        changePhoto.setOnAction(event -> changeAvatar());
        VBox identity = new VBox(8, avatar, email, changePhoto);
        identity.setAlignment(Pos.TOP_CENTER);

        city.getItems().setAll(new ArrayList<>(List.of("Bakı", "Sumqayıt", "Abşeron")));
        city.setOnAction(event -> fillDistricts(city.getValue(), ""));
        fullName.setPromptText(text("auth.full_name"));
        phone.setPromptText("+994 50 123 45 67");
        phonePublic.setText(text("user.profile.phone_public"));
        bio.setPromptText(text("user.profile.bio"));
        bio.setPrefRowCount(3);
        bio.setWrapText(true);
        bio.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change ->
                change.getControlNewText().length() <= 200 ? change : null));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.getColumnConstraints().setAll(labelColumn(), new javafx.scene.layout.ColumnConstraints());
        form.addRow(0, Components.label(text("auth.full_name"), "caption"), fullName);
        form.addRow(1, Components.label(text("discover.city"), "caption"), city);
        form.addRow(2, Components.label(text("profile.district"), "caption"), district);
        form.addRow(3, Components.label(text("profile.phone"), "caption"), phone);
        form.add(phonePublic, 1, 4);
        form.addRow(5, Components.label(text("user.profile.bio"), "caption"), bio);
        fullName.setPrefWidth(360);
        city.setPrefWidth(360);
        district.setPrefWidth(360);
        Button save = new Button(text("profile.save"));
        save.getStyleClass().add("primary");
        save.setOnAction(event -> save());
        VBox details = Components.card(text("profile.title"), form, Components.row(12, save, profileStatus));

        currentPassword.setPromptText(text("user.profile.password_current"));
        newPassword.setPromptText(text("user.profile.password_new"));
        Button changePassword = new Button(text("user.profile.password"));
        changePassword.getStyleClass().add("ghost");
        changePassword.setOnAction(event -> changePassword());
        passwordCard = Components.card(text("user.profile.password"), currentPassword, newPassword,
                Components.row(12, changePassword, passwordStatus));
        currentPassword.setMaxWidth(360);
        newPassword.setMaxWidth(360);

        VBox column = new VBox(16, Components.card(identity), details, passwordCard);
        column.setMaxWidth(640);
        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        return scroll;
    }

    static javafx.scene.layout.ColumnConstraints labelColumn() {
        javafx.scene.layout.ColumnConstraints column = new javafx.scene.layout.ColumnConstraints();
        column.setMinWidth(130);
        return column;
    }

    @Override
    public void refresh() {
        passwordCard.setVisible(context.graph().session().usesPassword());
        passwordCard.setManaged(context.graph().session().usesPassword());
        context.load(() -> context.graph().users().current(context.session()), this::fill);
    }

    private void fill(User loaded) {
        user = loaded;
        email.setText(loaded.email());
        fullName.setText(loaded.fullName());
        city.setValue(Pet.SUPPORTED_CITIES.contains(loaded.city()) ? loaded.city() : null);
        fillDistricts(city.getValue(), loaded.district());
        phone.setText(loaded.phone());
        phonePublic.setSelected(loaded.phonePublic());
        bio.setText(loaded.bio());
        profileStatus.setText("");
        renderAvatar(null);
        if (loaded.avatarPath() != null) {
            context.load(() -> context.graph().users().avatar(loaded), bytes -> renderAvatar(Components.image(bytes, 192, 192)),
                    code -> {
                    });
        }
    }

    private void fillDistricts(String selectedCity, String selected) {
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(Districts.of(selectedCity));
        district.getItems().setAll(options);
        district.setValue(options.contains(selected) ? selected : "");
    }

    private void renderAvatar(Image image) {
        if (image == null) {
            String name = user == null ? "" : user.fullName().trim();
            avatar.getChildren().setAll(Components.avatar(name.isEmpty() ? "🐾" : name.substring(0, 1).toUpperCase(context.locale()), 96));
            return;
        }
        javafx.scene.image.ImageView view = Components.cover(image, 96, 96);
        view.setClip(new Circle(48, 48, 48));
        avatar.getChildren().setAll(view);
    }

    private void save() {
        String name = fullName.getText();
        String chosenCity = city.getValue();
        String chosenDistrict = district.getValue() == null ? "" : district.getValue();
        String number = phone.getText();
        String about = bio.getText();
        boolean visible = phonePublic.isSelected();
        profileStatus.setText("…");
        context.load(() -> context.graph().users().updateProfile(context.session(), name, chosenCity, chosenDistrict,
                number, about, visible), saved -> {
            fill(saved);
            profileStatus.setText(text("user.profile.saved"));
        }, code -> profileStatus.setText(text(code)));
    }

    private void changeAvatar() {
        File file = Jpeg.pick(context.window(), text("image.files"));
        if (file == null) {
            return;
        }
        profileStatus.setText("…");
        context.load(() -> context.graph().users().updateAvatar(context.session(), Jpeg.encode(file, 1024, Jpeg.LISTING_BYTES)),
                saved -> {
                    fill(saved);
                    profileStatus.setText(text("user.profile.saved"));
                }, code -> profileStatus.setText(text(code)));
    }

    private void changePassword() {
        String current = currentPassword.getText();
        String next = newPassword.getText();
        passwordStatus.setText("…");
        context.load(() -> context.graph().auth().changePassword(current, next), session -> {
            currentPassword.clear();
            newPassword.clear();
            passwordStatus.setText(text("user.profile.password_changed"));
        }, code -> passwordStatus.setText(text(code)));
    }
}
