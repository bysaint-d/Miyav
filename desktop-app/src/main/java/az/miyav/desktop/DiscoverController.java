package az.miyav.desktop;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.AppException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.FavoriteRepository;
import az.miyav.domain.user.PublicProfile;
import az.miyav.security.AccessPolicy;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Public home of the desktop client. Opens without an account: browse, search, filter, view details and
 * photos, and keep favourites. Applying and contacting owners open the MIYAV sign-in dialog and resume
 * right after it.
 */
public final class DiscoverController {
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "miyav-images");
        thread.setDaemon(true);
        return thread;
    });
    private final MiyavDesktopApp app;
    private final DesktopGraph graph;
    private final Map<String, Image> thumbnails = new ConcurrentHashMap<>();
    private Pet selected;
    private boolean favorite;
    private int generation;

    @FXML private Label tagline;
    @FXML private Label modeBadge;
    @FXML private Button az;
    @FXML private Button en;
    @FXML private Button ru;
    @FXML private Button adminButton;
    @FXML private Button accountButton;
    @FXML private TextField search;
    @FXML private ComboBox<PetSpecies> species;
    @FXML private ComboBox<String> city;
    @FXML private ToggleButton savedOnly;
    @FXML private ListView<Pet> listings;
    @FXML private Label placeholder;
    @FXML private ImageView photo;
    @FXML private Label name;
    @FXML private Label facts;
    @FXML private Label description;
    @FXML private Label place;
    @FXML private Label publisher;
    @FXML private Label contact;
    @FXML private HBox actions;
    @FXML private Button favoriteButton;
    @FXML private Button applyButton;
    @FXML private Button contactButton;
    @FXML private Label status;
    @FXML private Hyperlink helpLink;
    @FXML private Hyperlink termsLink;
    @FXML private Hyperlink privacyLink;

    public DiscoverController(MiyavDesktopApp app) {
        this.app = app;
        this.graph = app.graph();
    }

    @FXML
    private void initialize() {
        tagline.setText(graph.text("discover.tagline"));
        az.setText(graph.text("language.az"));
        en.setText(graph.text("language.en"));
        ru.setText(graph.text("language.ru"));
        adminButton.setText(graph.text("discover.admin"));
        search.setPromptText(graph.text("discover.search"));
        savedOnly.setText("♥ " + graph.text("discover.saved_only"));
        applyButton.setText(graph.text("discover.apply"));
        helpLink.setText(graph.text("legal.help"));
        termsLink.setText(graph.text("legal.terms"));
        privacyLink.setText(graph.text("legal.privacy"));
        placeholder.setText(graph.text("discover.select"));
        photo.setCursor(Cursor.HAND);
        Tooltip.install(photo, new Tooltip(graph.text("photo.open")));
        photo.setOnMouseClicked(event -> {
            Pet pet = selected;
            if (pet != null && photo.getImage() != null) {
                PhotoViewer.open(window(), pet.name(), pet.photoPaths(), 0,
                        path -> graph.discovery().photo(graph.session().viewer(), path));
            }
        });

        List<PetSpecies> speciesValues = new ArrayList<>();
        speciesValues.add(null);
        speciesValues.addAll(List.of(PetSpecies.values()));
        species.getItems().setAll(speciesValues);
        species.setConverter(converter(value -> value == null
                ? graph.text("filter.all")
                : graph.text("species." + value.databaseValue())));
        species.getSelectionModel().selectFirst();
        species.setOnAction(event -> reload());
        List<String> cities = new ArrayList<>();
        cities.add(null);
        cities.addAll(List.of("Bakı", "Sumqayıt", "Abşeron"));
        city.getItems().setAll(cities);
        city.setConverter(converter(value -> value == null ? graph.text("filter.all") : value));
        city.getSelectionModel().selectFirst();
        city.setOnAction(event -> reload());

        listings.setCellFactory(view -> new PetCell());
        listings.getSelectionModel().selectedItemProperty().addListener((observable, before, pet) -> show(pet));
        show(null);
        onAuthChanged();
        reload();
    }

    /** Re-renders everything that depends on the account: header, admin entry, selected listing. */
    void onAuthChanged() {
        boolean signedIn = graph.session().signedIn();
        modeBadge.setText(signedIn ? "" : graph.text("guest.mode"));
        modeBadge.setVisible(!signedIn);
        accountButton.setText(graph.text(signedIn ? "auth.logout" : "guest.signin"));
        boolean admin = graph.session().decide(ProtectedAction.ADMIN) == AccessPolicy.Decision.ALLOWED;
        adminButton.setVisible(admin);
        adminButton.setManaged(admin);
        switch (graph.session().state()) {
            case SESSION_EXPIRED -> status.setText(graph.text("guest.session_expired"));
            case GUEST -> status.setText(graph.text("guest.profile.body"));
            default -> status.setText("");
        }
        if (selected != null) {
            show(selected);
        }
    }

    @FXML
    private void onSearch() {
        reload();
    }

    @FXML
    private void onAccount() {
        if (graph.session().signedIn()) {
            ButtonType signOut = new ButtonType(graph.text("auth.logout"), ButtonBar.ButtonData.OK_DONE);
            ButtonType delete = new ButtonType(graph.text("account.delete"), ButtonBar.ButtonData.OTHER);
            Alert choice = new Alert(Alert.AlertType.NONE, graph.text("account.menu_body"), signOut, delete, ButtonType.CANCEL);
            choice.initOwner(window());
            choice.setTitle(graph.text("account.menu_title"));
            choice.showAndWait().ifPresent(picked -> {
                if (picked == signOut) {
                    work(() -> {
                        graph.session().signOut();
                        return Boolean.TRUE;
                    }, done -> afterAccountChange(""));
                } else if (picked == delete) {
                    confirmDelete();
                }
            });
            return;
        }
        if (AuthDialog.show(window(), app, null)) {
            afterSignIn();
        }
    }

    @FXML
    private void onAdmin() {
        app.showAdmin();
    }

    @FXML
    private void onFavorite() {
        Pet pet = selected;
        if (pet == null) {
            return;
        }
        boolean next = !favorite;
        renderFavorite(next);
        work(() -> graph.favorites().set(graph.session().viewer(), pet.id(), next), saved -> {
            if (savedOnly.isSelected()) {
                reload();
            }
        });
    }

    @FXML
    private void onApply() {
        if (selected != null) {
            new ApplyDialog(selected).open();
        }
    }

    @FXML
    private void onContact() {
        Pet pet = selected;
        if (pet == null) {
            return;
        }
        PendingAction action = PendingAction.of(ProtectedAction.CONTACT_OWNER, "petId", pet.id().toString());
        if (gate(action)) {
            graph.session().takePending(ProtectedAction.CONTACT_OWNER, "petId", pet.id().toString());
            show(pet);
        }
    }

    @FXML
    private void onHelp() {
        info("legal.help", "legal.help_body");
    }

    @FXML
    private void onTerms() {
        info("legal.terms", "legal.terms_body");
    }

    @FXML
    private void onPrivacy() {
        info("legal.privacy", "legal.privacy_body");
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

    /**
     * Central gate for protected actions on this screen. Guests get the sign-in dialog; true means the
     * action may continue now (already signed in, or signed in through the dialog).
     */
    private boolean gate(PendingAction action) {
        AccessPolicy.Decision decision = graph.session().request(action);
        if (decision == AccessPolicy.Decision.ALLOWED) {
            return true;
        }
        if (decision == AccessPolicy.Decision.FORBIDDEN) {
            status.setText(graph.text("access.admin"));
            return false;
        }
        if (!AuthDialog.show(window(), app, action.action())) {
            return false;
        }
        afterSignIn();
        return true;
    }

    /** Refreshes account-specific data and offers to move device favourites to the account. */
    private void afterSignIn() {
        onAuthChanged();
        int count = graph.favorites().guestCount();
        if (count > 0) {
            Alert offer = new Alert(Alert.AlertType.CONFIRMATION);
            offer.initOwner(window());
            offer.setTitle(graph.text("favorites.sync_title"));
            offer.setHeaderText(graph.text("favorites.sync_title"));
            offer.setContentText(graph.text("favorites.sync_body", count));
            ButtonType yes = new ButtonType(graph.text("favorites.sync_yes"), ButtonBar.ButtonData.OK_DONE);
            ButtonType no = new ButtonType(graph.text("favorites.sync_no"), ButtonBar.ButtonData.CANCEL_CLOSE);
            offer.getButtonTypes().setAll(yes, no);
            if (offer.showAndWait().orElse(no) == yes) {
                work(() -> graph.favorites().syncGuestFavorites(graph.session().requireSession()), result -> {
                    FavoriteRepository.MergeResult merged = result;
                    status.setText(graph.text("favorites.synced", merged.added(), merged.alreadySaved(), merged.unavailable()));
                    reload();
                });
                return;
            }
        }
        reload();
    }

    private void reload() {
        int token = ++generation;
        boolean favoritesOnly = savedOnly.isSelected();
        PetQuery query = new PetQuery(species.getValue(), city.getValue() == null ? "" : city.getValue(), search.getText(), 50, 0);
        work(() -> {
            AuthSession viewer = graph.session().viewer();
            if (!favoritesOnly) {
                return graph.discovery().discover(viewer, query);
            }
            Set<UUID> ids = graph.favorites().ids(viewer);
            List<Pet> pets = graph.discovery().listings(ids);
            graph.favorites().forgetUnavailable(viewer, ids, pets);
            return pets;
        }, pets -> {
            if (token != generation) {
                return;
            }
            UUID keep = selected == null ? null : selected.id();
            listings.getItems().setAll(pets);
            listings.setPlaceholder(new Label(graph.text(favoritesOnly ? "favorites.empty" : "home.empty")));
            if (keep != null) {
                pets.stream().filter(pet -> pet.id().equals(keep)).findFirst()
                        .ifPresent(pet -> listings.getSelectionModel().select(pet));
            }
        }, code -> {
            if (token == generation) {
                listings.getItems().clear();
                listings.setPlaceholder(new Label(graph.text(code)));
            }
        });
    }

    private void show(Pet pet) {
        selected = pet;
        boolean visible = pet != null;
        placeholder.setVisible(!visible);
        placeholder.setManaged(!visible);
        for (javafx.scene.Node node : List.of(photo, name, facts, description, place, publisher, contact, actions)) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
        if (pet == null) {
            return;
        }
        name.setText(pet.name());
        String breed = pet.breed().isEmpty() ? "" : " · " + pet.breed();
        facts.setText(graph.text("species." + pet.species().databaseValue()) + breed
                + " · " + graph.text("discover.months", pet.ageMonths()) + " · " + pet.city());
        description.setText(pet.description());
        place.setText(pet.location() == null || pet.location().name().isEmpty()
                ? "" : graph.text("discover.location", pet.location().name()));
        photo.setImage(null);
        publisher.setText("");
        contact.setText("");
        boolean signedIn = graph.session().signedIn();
        contactButton.setText(graph.text("discover.contact"));
        contactButton.setVisible(!signedIn);
        contactButton.setManaged(!signedIn);
        renderFavorite(false);
        UUID petId = pet.id();
        if (!pet.photoPaths().isEmpty()) {
            image(pet.photoPaths().get(0), 960, image -> {
                if (selected != null && selected.id().equals(petId)) {
                    photo.setImage(image);
                }
            });
        }
        work(() -> new Publisher(
                graph.discovery().publisher(graph.session().viewer(), pet.ownerId()).orElse(null),
                graph.favorites().isFavorite(graph.session().viewer(), petId)
        ), loaded -> {
            if (selected == null || !selected.id().equals(petId)) {
                return;
            }
            renderFavorite(loaded.favorite);
            if (loaded.profile == null) {
                return;
            }
            publisher.setText(graph.text("discover.publisher") + ": " + loaded.profile.fullName()
                    + (loaded.profile.city().isEmpty() ? "" : " · " + loaded.profile.city()));
            if (graph.session().signedIn()) {
                contact.setText(loaded.profile.contactDigits().isPresent()
                        ? graph.text("discover.contact_phone", "+" + loaded.profile.contactDigits().get())
                        : graph.text("discover.no_contact"));
            }
        });
    }

    private void renderFavorite(boolean on) {
        favorite = on;
        favoriteButton.setText((on ? "♥ " : "♡ ") + graph.text(on ? "favorites.remove" : "favorites.add"));
    }

    private void image(String path, int size, Consumer<Image> onLoaded) {
        String key = size + ":" + path;
        Image cached = thumbnails.get(key);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }
        IMAGES.execute(() -> {
            try {
                byte[] bytes = graph.discovery().photo(graph.session().viewer(), path);
                Image image = new Image(new ByteArrayInputStream(bytes), size, size, true, true);
                if (!image.isError()) {
                    thumbnails.put(key, image);
                    Platform.runLater(() -> onLoaded.accept(image));
                }
            } catch (RuntimeException ignored) {
                // The listing still shows without its photo.
            }
        });
    }

    private void info(String titleKey, String bodyKey) {
        Stage stage = new Stage();
        stage.initOwner(window());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(graph.text(titleKey));
        Label title = new Label(graph.text(titleKey));
        title.getStyleClass().add("sheet-title");
        Label body = new Label(graph.text(bodyKey));
        body.setWrapText(true);
        body.setMaxWidth(520);
        Button close = new Button("OK");
        close.getStyleClass().add("primary");
        close.setDefaultButton(true);
        close.setOnAction(event -> stage.close());
        VBox root = new VBox(16, title, body, close);
        root.getStyleClass().addAll("screen", "sheet");
        root.setPadding(new Insets(28));
        root.setAlignment(Pos.TOP_LEFT);
        stage.setScene(scene(root));
        stage.showAndWait();
    }

    /** Permanent deletion after an explicit second confirmation. */
    private void confirmDelete() {
        ButtonType delete = new ButtonType(graph.text("account.delete_confirm"), ButtonBar.ButtonData.OK_DONE);
        Alert confirm = new Alert(Alert.AlertType.WARNING, graph.text("account.delete_body"), delete, ButtonType.CANCEL);
        confirm.initOwner(window());
        confirm.setTitle(graph.text("account.delete"));
        confirm.setHeaderText(graph.text("account.delete_title"));
        if (confirm.showAndWait().filter(delete::equals).isEmpty()) {
            return;
        }
        status.setText("…");
        work(() -> {
            graph.session().deleteAccount();
            return Boolean.TRUE;
        }, done -> afterAccountChange(graph.text("account.deleted")));
    }

    private void afterAccountChange(String message) {
        onAuthChanged();
        reload();
        if (!message.isEmpty()) {
            status.setText(message);
        }
    }

    private javafx.scene.Scene scene(javafx.scene.Parent root) {
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.getStylesheets().add(MiyavDesktopApp.class.getResource("miyav.css").toExternalForm());
        return scene;
    }

    private Window window() {
        return listings.getScene().getWindow();
    }

    private <T> void work(Supplier<T> task, Consumer<T> ok) {
        work(task, ok, code -> status.setText(graph.text(code)));
    }

    private <T> void work(Supplier<T> task, Consumer<T> ok, Consumer<String> fail) {
        Thread worker = new Thread(() -> {
            try {
                T result = task.get();
                Platform.runLater(() -> ok.accept(result));
            } catch (AccessDeniedException exception) {
                Platform.runLater(() -> {
                    if ("auth.session_expired".equals(exception.code())) {
                        graph.session().expire();
                        onAuthChanged();
                    } else {
                        fail.accept(exception.code());
                    }
                });
            } catch (AppException exception) {
                Platform.runLater(() -> fail.accept(exception.code()));
            } catch (RuntimeException exception) {
                Platform.runLater(() -> fail.accept("error.generic"));
            }
        }, "miyav-discover");
        worker.setDaemon(true);
        worker.start();
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    /**
     * Adoption application. A guest can write the message first; on submit the sign-in dialog opens over
     * it and the same text is sent right after sign-in.
     */
    private final class ApplyDialog {
        private final Pet pet;
        private final Stage stage = new Stage();
        private final TextArea message = new TextArea();
        private final Label error = new Label();
        private final Button send = new Button(graph.text("adoption.submit"));

        ApplyDialog(Pet pet) {
            this.pet = pet;
            stage.initOwner(window());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle(graph.text("discover.apply"));
            Label title = new Label(graph.text("discover.apply") + " · " + pet.name());
            title.getStyleClass().add("sheet-title");
            Label hint = new Label(graph.text("discover.apply_hint"));
            hint.setWrapText(true);
            hint.getStyleClass().add("muted");
            message.setWrapText(true);
            message.setPrefRowCount(6);
            message.setPromptText(graph.text("adoption.message"));
            error.setWrapText(true);
            error.getStyleClass().add("muted");
            send.getStyleClass().add("primary");
            send.setOnAction(event -> submit());
            VBox root = new VBox(12, title, hint, message, send, error);
            root.getStyleClass().addAll("screen", "sheet");
            root.setPadding(new Insets(24));
            root.setPrefWidth(480);
            stage.setScene(scene(root));
        }

        void open() {
            stage.show();
            message.requestFocus();
        }

        private void submit() {
            String text = message.getText() == null ? "" : message.getText().trim();
            if (text.length() < 10 || text.length() > 2000) {
                error.setText(graph.text("discover.apply_hint"));
                return;
            }
            PendingAction action = PendingAction.of(ProtectedAction.APPLY_ADOPTION, "petId", pet.id().toString())
                    .with("draft", text);
            AccessPolicy.Decision decision = graph.session().request(action);
            if (decision != AccessPolicy.Decision.ALLOWED) {
                if (!AuthDialog.show(stage, app, ProtectedAction.APPLY_ADOPTION)) {
                    return;
                }
                afterSignIn();
            }
            graph.session().takePending(ProtectedAction.APPLY_ADOPTION, "petId", pet.id().toString());
            send.setDisable(true);
            error.setText("");
            Thread worker = new Thread(() -> {
                try {
                    AuthSession session = graph.session().requireSession();
                    Pet current = graph.discovery().listing(session, pet.id())
                            .orElseThrow(() -> new AccessDeniedException("adoption.listing_unavailable"));
                    graph.adoptions().submit(session, graph.users().current(session), current, text);
                    Platform.runLater(() -> {
                        stage.close();
                        status.setText(graph.text("adoption.sent"));
                    });
                } catch (AppException exception) {
                    Platform.runLater(() -> {
                        send.setDisable(false);
                        error.setText(graph.text(exception.code()));
                    });
                }
            }, "miyav-apply");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static final class Publisher {
        final PublicProfile profile;
        final boolean favorite;

        Publisher(PublicProfile profile, boolean favorite) {
            this.profile = profile;
            this.favorite = favorite;
        }
    }

    private final class PetCell extends ListCell<Pet> {
        private final ImageView thumb = new ImageView();
        private final Label title = new Label();
        private final Label meta = new Label();
        private final HBox root;

        PetCell() {
            thumb.setFitWidth(64);
            thumb.setFitHeight(64);
            thumb.setPreserveRatio(true);
            title.getStyleClass().add("cell-title");
            meta.getStyleClass().add("muted");
            root = new HBox(12, thumb, new VBox(4, title, meta));
            root.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Pet pet, boolean empty) {
            super.updateItem(pet, empty);
            if (empty || pet == null) {
                setGraphic(null);
                return;
            }
            title.setText(pet.name());
            meta.setText(graph.text("species." + pet.species().databaseValue()) + " · " + pet.city());
            thumb.setImage(null);
            if (!pet.photoPaths().isEmpty()) {
                UUID id = pet.id();
                image(pet.photoPaths().get(0), 128, image -> {
                    if (getItem() != null && getItem().id().equals(id)) {
                        thumb.setImage(image);
                    }
                });
            }
            setGraphic(root);
        }
    }
}
