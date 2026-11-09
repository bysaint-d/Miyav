package az.miyav.desktop;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.AppException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.pet.AgeRange;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetFilters;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.pet.PetSort;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.FavoriteRepository;
import az.miyav.domain.repository.LiveUpdates;
import az.miyav.domain.user.PublicProfile;
import az.miyav.desktop.user.ApplicationsPage;
import az.miyav.desktop.user.MessagesPage;
import az.miyav.desktop.user.MyListingsPage;
import az.miyav.desktop.user.NotificationsPage;
import az.miyav.desktop.user.ProfilePage;
import az.miyav.desktop.user.UserContext;
import az.miyav.desktop.user.UserPage;
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
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import az.miyav.desktop.admin.Components;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final String DISCOVER = "discover";
    private static final double PHOTO_WIDTH = 520;
    private static final double PHOTO_HEIGHT = 340;
    private final Map<String, UserPage> pages = new LinkedHashMap<>();
    private final Map<String, ToggleButton> tabs = new LinkedHashMap<>();
    private MessagesPage messagesPage;
    private UserPage shown;
    private LiveUpdates.Subscription notificationFeed = LiveUpdates.NONE;
    private int unreadMessages;
    private int unreadNotifications;

    private WalkBoard walkBoard;
    private boolean walks;

    @FXML private ToggleButton adoptionMode;
    @FXML private ToggleButton walkMode;
    @FXML private BorderPane root;
    @FXML private HBox nav;
    @FXML private HBox searchRow;
    @FXML private HBox filterRow;
    @FXML private SplitPane discoverPane;
    @FXML private Button chatButton;
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
    @FXML private ComboBox<PetGender> gender;
    @FXML private ComboBox<AgeRange> age;
    @FXML private ToggleButton vaccinatedOnly;
    @FXML private ComboBox<PetSort> sort;
    @FXML private Hyperlink resetFilters;
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
        List<PetGender> genders = new ArrayList<>();
        genders.add(null);
        genders.add(PetGender.MALE);
        genders.add(PetGender.FEMALE);
        gender.getItems().setAll(genders);
        gender.setConverter(converter(value -> value == null
                ? graph.text("filters.gender") + ": " + graph.text("filter.all")
                : graph.text("gender." + value.databaseValue())));
        gender.getSelectionModel().selectFirst();
        gender.setOnAction(event -> reload());
        age.getItems().setAll(AgeRange.values());
        age.setConverter(converter(value -> graph.text("filters.age." + value.name().toLowerCase(java.util.Locale.ROOT))));
        age.getSelectionModel().select(AgeRange.ANY);
        age.setOnAction(event -> reload());
        vaccinatedOnly.setText("💉 " + graph.text("filters.vaccinated"));
        sort.getItems().setAll(PetSort.values());
        sort.setConverter(converter(value -> "↕ " + graph.text("filters.sort." + value.name().toLowerCase(java.util.Locale.ROOT))));
        sort.getSelectionModel().select(PetSort.NEWEST);
        sort.setOnAction(event -> reload());
        resetFilters.setText(graph.text("filters.reset"));

        listings.setCellFactory(view -> new PetCell());
        listings.getSelectionModel().selectedItemProperty().addListener((observable, before, pet) -> show(pet));
        chatButton.setText("💬 " + graph.text("discover.write_owner"));
        ToggleGroup modes = new ToggleGroup();
        adoptionMode.setToggleGroup(modes);
        walkMode.setToggleGroup(modes);
        adoptionMode.setText("🐾 " + graph.text("discover.mode_adoption"));
        walkMode.setText("🐕 " + graph.text("discover.mode_walks"));
        adoptionMode.setSelected(true);
        walkBoard = new WalkBoard(graph, this::window, status::setText, this::gate, (room, other, name) -> {
            messagesPage.prepare(room, other, name);
            openPage("messages", room);
        });
        BorderPane.setMargin(walkBoard.view(), new Insets(0, 24, 0, 24));
        buildNavigation();
        show(null);
        onAuthChanged();
        reload();
    }

    /** Discover plus the member pages; member pages ask guests to sign in first. */
    private void buildNavigation() {
        UserContext context = new UserContext(graph, status::setText, this::openPage, this::sessionLost,
                this::refreshBadges, this::window);
        messagesPage = new MessagesPage(context);
        for (UserPage page : List.of(new MyListingsPage(context), new ApplicationsPage(context), messagesPage,
                new NotificationsPage(context), new ProfilePage(context))) {
            pages.put(page.id(), page);
        }
        ToggleGroup group = new ToggleGroup();
        tabs.put(DISCOVER, tab(group, DISCOVER, "🔎", "user.nav.discover"));
        for (UserPage page : pages.values()) {
            tabs.put(page.id(), tab(group, page.id(), page.icon(), page.titleKey()));
        }
        tabs.get(DISCOVER).setSelected(true);
        nav.getChildren().setAll(tabs.values());
    }

    /** The screen is being replaced (language switch, admin console); stop live connections and timers. */
    void dispose() {
        stopLive();
    }

    private ToggleButton tab(ToggleGroup group, String id, String icon, String titleKey) {
        ToggleButton button = new ToggleButton(icon + "  " + graph.text(titleKey));
        button.getStyleClass().add("chip");
        button.setToggleGroup(group);
        button.setMnemonicParsing(false);
        button.setUserData(titleKey);
        button.setOnAction(event -> openPage(id, null));
        return button;
    }

    /** Switches the main area; member pages need an account. */
    private void openPage(String id, UUID focus) {
        if (!DISCOVER.equals(id) && !graph.session().signedIn()) {
            if (!AuthDialog.show(window(), app, null)) {
                selectTab(shown == null ? DISCOVER : shown.id());
                return;
            }
            afterSignIn();
        }
        if (shown != null) {
            shown.hidden();
        }
        selectTab(id);
        boolean discover = DISCOVER.equals(id);
        searchRow.setVisible(discover);
        searchRow.setManaged(discover);
        filterRow.setVisible(discover && !walks);
        filterRow.setManaged(discover && !walks);
        if (discover) {
            shown = null;
            root.setCenter(walks ? walkBoard.view() : discoverPane);
            return;
        }
        UserPage page = pages.get(id);
        shown = page;
        VBox frame = new VBox(page.view());
        VBox.setVgrow(page.view(), javafx.scene.layout.Priority.ALWAYS);
        frame.setPadding(new Insets(0, 24, 0, 24));
        root.setCenter(frame);
        page.refresh();
        if (focus != null) {
            page.focus(focus);
        }
    }

    private void selectTab(String id) {
        ToggleButton button = tabs.get(id);
        if (button != null) {
            button.setSelected(true);
        }
    }

    private void sessionLost() {
        graph.session().expire();
        onAuthChanged();
    }

    /** Unread chats and notifications on the navigation, updated live when a notification arrives. */
    private void refreshBadges() {
        if (!graph.session().signedIn()) {
            unreadMessages = 0;
            unreadNotifications = 0;
            renderBadges();
            return;
        }
        work(() -> {
            AuthSession session = graph.session().requireSession();
            return new int[]{graph.messaging().unreadCount(session), graph.notifications().unreadCount(session)};
        }, counts -> {
            unreadMessages = counts[0];
            unreadNotifications = counts[1];
            renderBadges();
        }, code -> {
        });
    }

    private void renderBadges() {
        badge("messages", unreadMessages);
        badge("notifications", unreadNotifications);
    }

    private void badge(String id, int count) {
        ToggleButton button = tabs.get(id);
        UserPage page = pages.get(id);
        if (button != null && page != null) {
            button.setText(page.icon() + "  " + graph.text(page.titleKey()) + (count > 0 ? "  (" + count + ")" : ""));
        }
    }

    private void startLive() {
        stopLive();
        if (!graph.session().signedIn()) {
            return;
        }
        notificationFeed = graph.live().watchNotifications(() -> graph.session().viewer(), () -> Platform.runLater(() -> {
            refreshBadges();
            if (shown != null && ("notifications".equals(shown.id()) || "applications".equals(shown.id()))) {
                shown.refresh();
            }
        }));
    }

    private void stopLive() {
        notificationFeed.close();
        notificationFeed = LiveUpdates.NONE;
        if (shown != null) {
            shown.hidden();
        }
    }

    @FXML
    private void onChat() {
        Pet pet = selected;
        if (pet == null) {
            return;
        }
        PendingAction action = PendingAction.of(ProtectedAction.CONTACT_OWNER, "petId", pet.id().toString());
        if (!gate(action)) {
            return;
        }
        graph.session().takePending(ProtectedAction.CONTACT_OWNER, "petId", pet.id().toString());
        work(() -> {
            AuthSession session = graph.session().requireSession();
            UUID room = graph.messaging().openForAdoption(session, pet);
            String name = graph.discovery().publisher(session, pet.ownerId()).map(PublicProfile::fullName).orElse("");
            return new OwnerChat(room, name);
        }, chat -> {
            messagesPage.prepare(chat.room(), pet.ownerId(), chat.ownerName());
            openPage("messages", chat.room());
        });
    }

    private record OwnerChat(UUID room, String ownerName) {
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
        if (!signedIn && shown != null) {
            openPage(DISCOVER, null);
        }
        if (signedIn) {
            startLive();
        } else {
            stopLive();
        }
        refreshBadges();
        if (selected != null) {
            show(selected);
        }
        walkBoard.refreshSelection();
    }

    @FXML
    private void onSearch() {
        reload();
    }

    @FXML
    private void onAdoptionMode() {
        setWalks(false);
    }

    @FXML
    private void onWalkMode() {
        setWalks(true);
    }

    /** Adoption listings or paid walk requests; walks keep only the species and city filters. */
    private void setWalks(boolean next) {
        walks = next;
        adoptionMode.setSelected(!next);
        walkMode.setSelected(next);
        for (javafx.scene.Node node : List.of(search, savedOnly)) {
            node.setVisible(!next);
            node.setManaged(!next);
        }
        filterRow.setVisible(!next);
        filterRow.setManaged(!next);
        if (shown == null) {
            root.setCenter(next ? walkBoard.view() : discoverPane);
        }
        reload();
    }

    @FXML
    private void onResetFilters() {
        gender.getSelectionModel().selectFirst();
        age.getSelectionModel().select(AgeRange.ANY);
        vaccinatedOnly.setSelected(false);
        sort.getSelectionModel().select(PetSort.NEWEST);
        reload();
    }

    private PetFilters filters() {
        return new PetFilters(gender.getValue(), age.getValue(), vaccinatedOnly.isSelected(), sort.getValue());
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
        graph.syncProfileLocale();
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
        if (walks) {
            walkBoard.reload(species.getValue(), city.getValue());
            return;
        }
        int token = ++generation;
        boolean favoritesOnly = savedOnly.isSelected();
        PetFilters filters = filters();
        resetFilters.setVisible(filters.activeCount() > 0);
        PetQuery query = new PetQuery(species.getValue(), city.getValue() == null ? "" : city.getValue(), search.getText(), 50, 0, filters);
        work(() -> {
            AuthSession viewer = graph.session().viewer();
            if (!favoritesOnly) {
                return graph.discovery().discover(viewer, query);
            }
            Set<UUID> ids = graph.favorites().ids(viewer);
            List<Pet> pets = graph.discovery().listings(ids);
            graph.favorites().forgetUnavailable(viewer, ids, pets);
            return pets.stream().filter(filters::matches).toList();
        }, pets -> {
            if (token != generation) {
                return;
            }
            UUID keep = selected == null ? null : selected.id();
            listings.getItems().setAll(pets);
            listings.setPlaceholder(new Label(graph.text(favoritesOnly ? "favorites.empty"
                    : filters.activeCount() > 0 ? "filters.empty" : "home.empty")));
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
                + " · " + graph.text("gender." + pet.gender().databaseValue())
                + " · " + graph.text("discover.months", pet.ageMonths())
                + " · " + graph.text("vaccination." + pet.vaccination().databaseValue())
                + " · " + pet.city());
        description.setText(pet.description());
        place.setText(pet.location() == null || pet.location().name().isEmpty()
                ? "" : graph.text("discover.location", pet.location().name()));
        Components.fill(photo, null, PHOTO_WIDTH, PHOTO_HEIGHT, 18);
        publisher.setText("");
        contact.setText("");
        boolean signedIn = graph.session().signedIn();
        contactButton.setText(graph.text("discover.contact"));
        contactButton.setVisible(!signedIn);
        contactButton.setManaged(!signedIn);
        boolean own = graph.session().session().map(session -> session.userId().equals(pet.ownerId())).orElse(false);
        chatButton.setVisible(!own);
        chatButton.setManaged(!own);
        renderFavorite(false);
        UUID petId = pet.id();
        if (!pet.photoPaths().isEmpty()) {
            image(pet.photoPaths().get(0), 1040, image -> {
                if (selected != null && selected.id().equals(petId)) {
                    Components.fill(photo, image, PHOTO_WIDTH, PHOTO_HEIGHT, 18);
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
        private static final double THUMB = 76;
        private final ImageView thumb = new ImageView();
        private final Label glyph = new Label();
        private final Label title = new Label();
        private final Label meta = new Label();
        private final HBox root;

        PetCell() {
            StackPane frame = new StackPane(glyph, thumb);
            frame.getStyleClass().add("photo-placeholder");
            frame.setMinSize(THUMB, THUMB);
            frame.setMaxSize(THUMB, THUMB);
            glyph.getStyleClass().add("nav-icon");
            title.getStyleClass().add("cell-title");
            meta.getStyleClass().add("muted");
            root = new HBox(12, frame, new VBox(4, title, meta));
            root.setAlignment(Pos.CENTER_LEFT);
            root.setMinHeight(THUMB + 8);
        }

        @Override
        protected void updateItem(Pet pet, boolean empty) {
            super.updateItem(pet, empty);
            if (empty || pet == null) {
                setGraphic(null);
                return;
            }
            title.setText(pet.name());
            meta.setText(graph.text("species." + pet.species().databaseValue()) + " · "
                    + graph.text("discover.months", pet.ageMonths()) + " · " + pet.city());
            glyph.setText(Components.speciesGlyph(pet.species().databaseValue()));
            Components.fill(thumb, null, THUMB, THUMB, 12);
            if (!pet.photoPaths().isEmpty()) {
                UUID id = pet.id();
                image(pet.photoPaths().get(0), 240, image -> {
                    if (getItem() != null && getItem().id().equals(id)) {
                        Components.fill(thumb, image, THUMB, THUMB, 12);
                    }
                });
            }
            setGraphic(root);
        }
    }
}
