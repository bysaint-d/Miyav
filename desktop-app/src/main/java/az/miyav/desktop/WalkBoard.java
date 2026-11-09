package az.miyav.desktop;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.AppException;
import az.miyav.desktop.admin.Components;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkOffer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * "Gəzdirmə" on the desktop: owners' paid walk requests. Anyone can browse; offering a price or writing to
 * the owner needs an account, like on the phone.
 */
final class WalkBoard {
    private static final double THUMB = 76;
    private static final double PHOTO_WIDTH = 520;
    private static final double PHOTO_HEIGHT = 300;

    /** Opens the member chat page on a conversation. */
    interface ChatOpener {
        void open(UUID room, UUID otherUserId, String otherName);
    }

    private final DesktopGraph graph;
    private final Supplier<Window> window;
    private final Consumer<String> status;
    private final Predicate<PendingAction> gate;
    private final ChatOpener chats;
    private final Map<String, Image> photos = new ConcurrentHashMap<>();
    private final ListView<WalkListing> list = new ListView<>();
    private final Label placeholder = Components.wrapped("", "muted");
    private final ImageView photo = new ImageView();
    private final Label title = Components.label("", "detail-title");
    private final Label price = Components.label("", "card-title");
    private final Label when = Components.wrapped("", "muted");
    private final Label place = Components.wrapped("", "muted");
    private final Label notes = Components.wrapped("");
    private final Label owner = Components.wrapped("");
    private final Label offerState = Components.wrapped("", "muted");
    private final Button offer = new Button();
    private final Button chat = new Button();
    private final VBox details;
    private final SplitPane view;
    private WalkListing selected;
    private int generation;

    WalkBoard(DesktopGraph graph, Supplier<Window> window, Consumer<String> status, Predicate<PendingAction> gate,
              ChatOpener chats) {
        this.graph = graph;
        this.window = window;
        this.status = status;
        this.gate = gate;
        this.chats = chats;
        list.setCellFactory(cells -> new WalkCell());
        list.getSelectionModel().selectedItemProperty().addListener((observable, before, listing) -> show(listing));
        offer.getStyleClass().add("primary");
        offer.setOnAction(event -> offer());
        chat.getStyleClass().add("ghost");
        chat.setOnAction(event -> chat());
        HBox actions = new HBox(8, offer, chat);
        details = new VBox(12, photo, title, price, when, place, notes, owner, actions, offerState);
        details.getStyleClass().add("detail");
        details.setPadding(new Insets(20, 24, 24, 24));
        StackPane detailPane = new StackPane(details, placeholder);
        StackPane.setAlignment(placeholder, Pos.TOP_LEFT);
        StackPane.setMargin(placeholder, new Insets(20, 24, 24, 24));
        ScrollPane scroll = new ScrollPane(detailPane);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        view = new SplitPane(list, scroll);
        view.setDividerPositions(0.4);
        view.getStyleClass().add("split");
        show(null);
    }

    Node view() {
        return view;
    }

    void reload(PetSpecies species, String city) {
        int token = ++generation;
        list.setPlaceholder(new Label("…"));
        placeholder.setText(graph.text("walk.board_select"));
        offer.setText("💰 " + graph.text("walk.offer"));
        chat.setText("💬 " + graph.text("discover.write_owner"));
        run(() -> graph.walkListings().board(graph.session().viewer(), species, city == null ? "" : city, 50, 0), listings -> {
            if (token != generation) {
                return;
            }
            UUID keep = selected == null ? null : selected.id();
            list.getItems().setAll(listings);
            list.setPlaceholder(new Label(graph.text("walk.board_empty")));
            if (keep != null) {
                listings.stream().filter(item -> item.id().equals(keep)).findFirst()
                        .ifPresent(item -> list.getSelectionModel().select(item));
            }
        });
    }

    /** Re-renders the open listing after sign-in or sign-out. */
    void refreshSelection() {
        if (selected != null) {
            show(selected);
        }
    }

    private void show(WalkListing listing) {
        selected = listing;
        boolean visible = listing != null;
        details.setVisible(visible);
        details.setManaged(visible);
        placeholder.setVisible(!visible);
        placeholder.setManaged(!visible);
        if (listing == null) {
            return;
        }
        WalkListingDetails info = listing.details();
        Locale locale = Locale.forLanguageTag(graph.catalog().locale().tag());
        title.setText(Components.speciesGlyph(info.species().databaseValue()) + "  " + info.petName());
        price.setText(graph.text("walk.per_hour", money(listing.effectivePricePerHour()))
                + "  ·  " + graph.text("walk.total", money(listing.effectiveTotal())));
        when.setText("🗓  " + Components.dateTime(info.startsAt(), locale) + "  ·  " + duration(info.durationMinutes()));
        String area = info.area() == null || info.area().name().isEmpty() ? info.city() : info.area().name();
        place.setText("📍  " + area);
        notes.setText(info.notes());
        notes.setVisible(!info.notes().isBlank());
        notes.setManaged(!info.notes().isBlank());
        owner.setText("");
        offerState.setText("");
        Components.fill(photo, null, PHOTO_WIDTH, PHOTO_HEIGHT, 18);
        listing.petPhotoPath().ifPresent(path -> image(path, 1040, image -> {
            if (selected != null && selected.id().equals(listing.id())) {
                Components.fill(photo, image, PHOTO_WIDTH, PHOTO_HEIGHT, 18);
            }
        }));
        boolean own = graph.session().session().map(session -> listing.ownedBy(session.userId())).orElse(false);
        boolean open = listing.acceptsOffers(Instant.now());
        offer.setVisible(!own && open);
        offer.setManaged(!own && open);
        chat.setVisible(!own);
        chat.setManaged(!own);
        if (own) {
            offerState.setText(graph.text("walk.board_own"));
        } else if (!open) {
            offerState.setText(graph.text("walk.listing_closed"));
        }
        UUID id = listing.id();
        run(() -> new OwnerAndOffer(
                graph.discovery().publisher(graph.session().viewer(), listing.ownerId()).orElse(null),
                graph.session().signedIn() && !own
                        ? graph.walkListings().myOfferFor(graph.session().requireSession(), listing).orElse(null) : null
        ), loaded -> {
            if (selected == null || !selected.id().equals(id)) {
                return;
            }
            if (loaded.profile != null) {
                owner.setText("👤  " + graph.text("discover.publisher") + ": " + loaded.profile.fullName()
                        + (loaded.profile.city().isEmpty() ? "" : " · " + loaded.profile.city()));
            }
            if (loaded.offer != null) {
                offerState.setText(graph.text("walk.offer_mine", money(loaded.offer.priceFor(listing.details())),
                        graph.text("walk.status." + loaded.offer.status().name().toLowerCase(Locale.ROOT))));
            }
        });
    }

    private void offer() {
        WalkListing listing = selected;
        if (listing == null || !gate.test(PendingAction.of(ProtectedAction.OFFER_WALK, "listingId", listing.id().toString()))) {
            return;
        }
        graph.session().takePending(ProtectedAction.OFFER_WALK, "listingId", listing.id().toString());
        TextInputDialog dialog = new TextInputDialog(money(listing.details().pricePerHour()));
        dialog.initOwner(window.get());
        dialog.setTitle(graph.text("walk.offer"));
        dialog.setHeaderText(graph.text("walk.offer_title", listing.details().petName()));
        dialog.setContentText(graph.text("walk.offer_price"));
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(answer.get().trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            status.accept(graph.text("validation.walk_price"));
            return;
        }
        run(() -> graph.walkListings().offer(graph.session().requireSession(), listing, value, ""), sent -> {
            status.accept(graph.text("walk.offer_sent"));
            show(listing);
        });
    }

    private void chat() {
        WalkListing listing = selected;
        if (listing == null || !gate.test(PendingAction.of(ProtectedAction.SEND_MESSAGE, "listingId", listing.id().toString()))) {
            return;
        }
        graph.session().takePending(ProtectedAction.SEND_MESSAGE, "listingId", listing.id().toString());
        run(() -> {
            AuthSession session = graph.session().requireSession();
            UUID room = graph.messaging().openForWalk(session, listing, null);
            String name = graph.discovery().publisher(session, listing.ownerId()).map(PublicProfile::fullName).orElse("");
            return new Room(room, name);
        }, room -> chats.open(room.id, listing.ownerId(), room.name));
    }

    private String duration(int minutes) {
        if (minutes % 60 == 0) {
            return graph.text("walk.duration_hours", minutes / 60);
        }
        return minutes > 60 ? graph.text("walk.duration_hours_minutes", minutes / 60, minutes % 60)
                : graph.text("walk.duration_minutes", minutes);
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "—" : amount.stripTrailingZeros().toPlainString();
    }

    private void image(String path, int size, Consumer<Image> onLoaded) {
        String key = size + ":" + path;
        Image cached = photos.get(key);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }
        run(() -> Components.image(graph.walkListings().petPhoto(graph.session().viewer(), path), size, size), image -> {
            if (image != null) {
                photos.put(key, image);
                onLoaded.accept(image);
            }
        }, code -> {
        });
    }

    private <T> void run(Supplier<T> task, Consumer<T> ok) {
        run(task, ok, code -> status.accept(graph.text(code)));
    }

    private <T> void run(Supplier<T> task, Consumer<T> ok, Consumer<String> failed) {
        Thread worker = new Thread(() -> {
            try {
                T result = task.get();
                Platform.runLater(() -> ok.accept(result));
            } catch (AccessDeniedException exception) {
                Platform.runLater(() -> failed.accept(exception.code()));
            } catch (AppException exception) {
                Platform.runLater(() -> failed.accept(exception.code()));
            } catch (RuntimeException exception) {
                Platform.runLater(() -> failed.accept("error.generic"));
            }
        }, "miyav-walk-board");
        worker.setDaemon(true);
        worker.start();
    }

    private record OwnerAndOffer(PublicProfile profile, WalkOffer offer) {
    }

    private record Room(UUID id, String name) {
    }

    private final class WalkCell extends ListCell<WalkListing> {
        private final ImageView thumb = new ImageView();
        private final Label glyph = Components.label("", "nav-icon");
        private final Label name = Components.label("", "cell-title");
        private final Label meta = Components.label("", "muted");
        private final Label money = Components.label("", "card-title");
        private final HBox root;

        WalkCell() {
            StackPane frame = new StackPane(glyph, thumb);
            frame.getStyleClass().add("photo-placeholder");
            frame.setMinSize(THUMB, THUMB);
            frame.setMaxSize(THUMB, THUMB);
            root = new HBox(12, frame, new VBox(4, money, name, meta));
            root.setAlignment(Pos.CENTER_LEFT);
            root.setMinHeight(THUMB + 8);
        }

        @Override
        protected void updateItem(WalkListing listing, boolean empty) {
            super.updateItem(listing, empty);
            if (empty || listing == null) {
                setGraphic(null);
                return;
            }
            WalkListingDetails info = listing.details();
            Locale locale = Locale.forLanguageTag(graph.catalog().locale().tag());
            glyph.setText(Components.speciesGlyph(info.species().databaseValue()));
            money.setText(graph.text("walk.per_hour", WalkBoard.money(listing.effectivePricePerHour())));
            name.setText(info.petName());
            meta.setText(Components.dateTime(info.startsAt(), locale) + " · " + duration(info.durationMinutes()) + " · " + info.city());
            Components.fill(thumb, null, THUMB, THUMB, 12);
            UUID id = listing.id();
            listing.petPhotoPath().ifPresent(path -> image(path, 240, image -> {
                if (getItem() != null && getItem().id().equals(id)) {
                    Components.fill(thumb, image, THUMB, THUMB, 12);
                }
            }));
            setGraphic(root);
        }
    }
}
