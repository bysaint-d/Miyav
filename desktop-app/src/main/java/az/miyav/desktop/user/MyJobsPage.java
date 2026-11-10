package az.miyav.desktop.user;

import az.miyav.desktop.admin.Components;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.walk.MyWalks;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkListingStatus;
import az.miyav.domain.walk.WalkOffer;
import az.miyav.domain.walk.WalkerRating;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * "İşlərim", as on the phone: the walks I posted (offers to answer), the offers I sent and the agreed walks.
 * Owners choose a walker, cancel, mark the walk done and rate the walker; walkers mark it done too.
 */
public final class MyJobsPage extends UserPage {
    private final ToggleGroup tabs = new ToggleGroup();
    private final ListView<WalkListing> list = new ListView<>();
    private final VBox detail = new VBox(12);
    private MyWalks walks;
    private UUID me;

    public MyJobsPage(UserContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "jobs";
    }

    @Override
    public String titleKey() {
        return "user.nav.jobs";
    }

    @Override
    public String icon() {
        return "📋";
    }

    @Override
    protected Node build() {
        ToggleButton requests = tab("user.jobs.requests", "requests", true);
        ToggleButton offers = tab("user.jobs.offers", "offers", false);
        ToggleButton agreed = tab("user.jobs.agreed", "agreed", false);
        Button create = new Button("＋  " + text("user.jobs.new"));
        create.getStyleClass().add("primary");
        create.setOnAction(event -> createListing());
        HBox header = Components.row(8, requests, offers, agreed, Components.spacer(), create);

        list.setCellFactory(view -> new JobCell());
        list.setPlaceholder(Components.wrapped(text("user.jobs.empty_requests"), "muted"));
        list.setPrefWidth(380);
        list.getSelectionModel().selectedItemProperty().addListener((observable, before, picked) -> show(picked));
        detail.setPadding(new Insets(4, 4, 24, 4));
        ScrollPane scroll = new ScrollPane(detail);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        HBox.setHgrow(scroll, Priority.ALWAYS);
        HBox body = new HBox(16, list, scroll);
        VBox.setVgrow(body, Priority.ALWAYS);
        showEmptyDetail();
        return new VBox(16, header, body);
    }

    private ToggleButton tab(String key, String id, boolean selected) {
        ToggleButton button = new ToggleButton(text(key));
        button.getStyleClass().add("chip");
        button.setUserData(id);
        button.setToggleGroup(tabs);
        button.setSelected(selected);
        button.setOnAction(event -> {
            if (!button.isSelected()) {
                button.setSelected(true);
            }
            render();
        });
        return button;
    }

    @Override
    public void refresh() {
        context.load(() -> {
            AuthSession session = context.session();
            return new Object[]{session.userId(), context.graph().walkListings().myWalks(session)};
        }, loaded -> {
            me = (UUID) loaded[0];
            walks = (MyWalks) loaded[1];
            render();
        });
    }

    private String tabId() {
        return tabs.getSelectedToggle() == null ? "requests" : (String) tabs.getSelectedToggle().getUserData();
    }

    private void render() {
        if (walks == null) {
            return;
        }
        Instant now = Instant.now();
        UUID selected = list.getSelectionModel().getSelectedItem() == null ? null : list.getSelectionModel().getSelectedItem().id();
        List<WalkListing> shown = switch (tabId()) {
            case "offers" -> walks.sentOffers();
            case "agreed" -> walks.agreements(now);
            default -> walks.requests(now);
        };
        list.setPlaceholder(Components.wrapped(text(switch (tabId()) {
            case "offers" -> "user.jobs.empty_offers";
            case "agreed" -> "user.jobs.empty_agreed";
            default -> "user.jobs.empty_requests";
        }), "muted"));
        list.getItems().setAll(shown);
        shown.stream().filter(listing -> listing.id().equals(selected)).findFirst()
                .ifPresentOrElse(listing -> list.getSelectionModel().select(listing), this::showEmptyDetail);
    }

    private void showEmptyDetail() {
        detail.getChildren().setAll(Components.card(Components.empty("📋", text("user.jobs.select_title"),
                text("user.jobs.select_body"))));
    }

    private String stage(WalkListing listing) {
        MyWalks.Stage stage = walks.stage(listing, Instant.now());
        return text("user.jobs.stage." + stage.name().toLowerCase(Locale.ROOT), walks.pendingOffers(listing.id()));
    }

    private void show(WalkListing listing) {
        if (listing == null) {
            showEmptyDetail();
            return;
        }
        boolean owner = listing.ownedBy(me);
        WalkListingDetails details = listing.details();
        VBox info = Components.card(
                Components.label(Components.speciesGlyph(details.species().databaseValue()) + "  " + details.petName(), "detail-title"),
                Components.badge(stage(listing), Components.Tone.PEACH),
                Components.label(text("walk.per_hour", money(listing.effectivePricePerHour())) + " · "
                        + text("walk.total", money(listing.effectiveTotal())), "card-title"),
                Components.wrapped("📅 " + Components.dateTime(details.startsAt(), context.locale()) + " · ⏱ "
                        + duration(details.durationMinutes()), "muted"),
                Components.wrapped("📍 " + details.city() + (details.area() == null || details.area().name().isEmpty()
                        ? "" : ", " + details.area().name()), "muted"));
        if (!details.notes().isBlank()) {
            info.getChildren().add(Components.wrapped(details.notes()));
        }
        VBox actions = new VBox(8);
        VBox people = new VBox(10);
        detail.getChildren().setAll(info, people, actions);
        renderActions(listing, owner, actions);
        context.load(() -> loadPeople(listing, owner), loaded -> {
            if (list.getSelectionModel().getSelectedItem() == listing) {
                renderPeople(listing, owner, loaded, people);
            }
        });
    }

    private People loadPeople(WalkListing listing, boolean owner) {
        AuthSession session = context.session();
        List<WalkOffer> offers = owner ? context.graph().walkListings().offersFor(session, listing) : List.of();
        Map<UUID, PublicProfile> profiles = new HashMap<>();
        List<UUID> walkers = new ArrayList<>();
        listing.walkerId().ifPresent(walkers::add);
        offers.forEach(offer -> walkers.add(offer.walkerId()));
        for (UUID id : walkers) {
            context.graph().discovery().publisher(session, id).ifPresent(profile -> profiles.put(id, profile));
        }
        if (!owner) {
            context.graph().discovery().publisher(session, listing.ownerId()).ifPresent(profile -> profiles.put(listing.ownerId(), profile));
        }
        Map<UUID, WalkerRating> ratings = walkers.isEmpty() ? Map.of() : context.graph().walkListings().walkerRatings(session, walkers);
        return new People(offers, profiles, ratings);
    }

    private void renderPeople(WalkListing listing, boolean owner, People loaded, VBox people) {
        people.getChildren().clear();
        if (!owner) {
            people.getChildren().add(Components.card(text("user.jobs.owner"),
                    Components.label(name(loaded.profiles.get(listing.ownerId())), "cell-title")));
        }
        listing.walkerId().ifPresent(walker -> {
            VBox card = Components.card(text("user.jobs.walker"),
                    Components.label(name(loaded.profiles.get(walker)) + rating(loaded.ratings.get(walker)), "cell-title"));
            listing.walkerRating().ifPresent(stars -> card.getChildren().add(Components.wrapped(
                    text("user.jobs.rated", "★".repeat(stars) + "☆".repeat(5 - stars))
                            + (listing.walkerReview().isBlank() ? "" : "\n“" + listing.walkerReview() + "”"))));
            people.getChildren().add(card);
        });
        if (!owner || listing.status() == WalkListingStatus.CANCELLED) {
            return;
        }
        VBox offers = Components.card(text("user.jobs.offers_title", loaded.offers.stream().filter(o -> o.status() != WalkOffer.Status.WITHDRAWN).count()));
        boolean open = listing.status() == WalkListingStatus.OPEN;
        for (WalkOffer offer : loaded.offers) {
            if (offer.status() == WalkOffer.Status.WITHDRAWN) {
                continue;
            }
            Label who = Components.label(name(loaded.profiles.get(offer.walkerId())) + rating(loaded.ratings.get(offer.walkerId())), "cell-title");
            Label price = Components.label(text("walk.per_hour", money(offer.priceFor(listing.details()))), "card-title");
            VBox text = new VBox(4, who, price);
            if (!offer.message().isBlank()) {
                text.getChildren().add(Components.wrapped(offer.message(), "muted"));
            }
            HBox row = Components.row(12, text, Components.spacer());
            if (open && offer.pending()) {
                Button accept = new Button(text("user.jobs.accept"));
                accept.getStyleClass().add("primary");
                accept.setOnAction(event -> confirm(text("user.jobs.accept_confirm", name(loaded.profiles.get(offer.walkerId()))),
                        () -> context.graph().walkListings().respond(context.session(), listing, offer, true), "user.jobs.accepted"));
                Button decline = new Button(text("user.jobs.decline"));
                decline.getStyleClass().add("ghost");
                decline.setOnAction(event -> act(() -> context.graph().walkListings().respond(context.session(), listing, offer, false),
                        "user.jobs.declined"));
                row.getChildren().addAll(accept, decline);
            } else {
                row.getChildren().add(Components.badge(text("walk.status." + offer.status().name().toLowerCase(Locale.ROOT)),
                        Components.Tone.NEUTRAL));
            }
            offers.getChildren().add(row);
        }
        if (loaded.offers.isEmpty()) {
            offers.getChildren().add(Components.wrapped(text("user.jobs.no_offers"), "muted"));
        }
        people.getChildren().add(offers);
    }

    private void renderActions(WalkListing listing, boolean owner, VBox actions) {
        Instant now = Instant.now();
        boolean walker = listing.walkedBy(me);
        if ((owner || walker) && listing.completable(now)) {
            Button complete = new Button("🏁  " + text("user.jobs.complete"));
            complete.getStyleClass().add("primary");
            complete.setOnAction(event -> act(() -> context.graph().walkListings().complete(context.session(), listing),
                    "user.jobs.completed"));
            actions.getChildren().add(complete);
        }
        if (listing.rateableBy(me)) {
            Button rate = new Button("★  " + text("user.jobs.rate"));
            rate.getStyleClass().add("primary");
            rate.setOnAction(event -> rate(listing));
            actions.getChildren().add(rate);
        }
        if (owner || walker || walks.myOffer(listing.id()).isPresent()) {
            Button chat = new Button("💬  " + text("user.jobs.chat"));
            chat.getStyleClass().add("ghost");
            chat.setOnAction(event -> chat(listing, owner));
            chat.setDisable(owner && listing.walkerId().isEmpty());
            actions.getChildren().add(chat);
        }
        if (owner && listing.status().active()) {
            Button cancel = new Button(text("user.jobs.cancel"));
            cancel.getStyleClass().add("danger");
            cancel.setOnAction(event -> confirm(text("user.jobs.cancel_confirm"),
                    () -> context.graph().walkListings().cancel(context.session(), listing), "user.jobs.cancelled"));
            actions.getChildren().add(cancel);
        }
        Optional<WalkOffer> mine = walks.myOffer(listing.id());
        if (!owner && mine.isPresent() && mine.get().pending()) {
            Button withdraw = new Button(text("user.jobs.withdraw"));
            withdraw.getStyleClass().add("ghost");
            withdraw.setOnAction(event -> act(() -> context.graph().walkListings().withdraw(context.session(), mine.get()),
                    "user.jobs.withdrawn"));
            actions.getChildren().add(withdraw);
        }
        actions.getChildren().forEach(node -> ((Button) node).setMaxWidth(320));
    }

    private void chat(WalkListing listing, boolean owner) {
        context.load(() -> context.graph().messaging().openForWalk(context.session(), listing,
                owner ? listing.walkerId().orElse(null) : null), room -> context.open("messages", room));
    }

    private void rate(WalkListing listing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(context.window());
        dialog.setTitle(text("user.jobs.rate"));
        ComboBox<Integer> stars = new ComboBox<>();
        stars.getItems().setAll(5, 4, 3, 2, 1);
        stars.setValue(5);
        stars.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : "★".repeat(value) + "☆".repeat(5 - value);
            }

            @Override
            public Integer fromString(String value) {
                return null;
            }
        });
        TextArea review = new TextArea();
        review.setPromptText(text("user.jobs.review_hint"));
        review.setPrefRowCount(3);
        review.setWrapText(true);
        review.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change ->
                change.getControlNewText().length() <= 300 ? change : null));
        dialog.getDialogPane().setContent(new VBox(10, stars, review));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(ok -> {
            int value = stars.getValue();
            String text = review.getText();
            act(() -> context.graph().walkListings().rate(context.session(), listing, value, text), "user.jobs.rated_done");
        });
    }

    /** A new walk request, like the phone's form: pet, when, how long, the hourly price, city and a note. */
    private void createListing() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(context.window());
        dialog.setTitle(text("user.jobs.new"));
        TextField pet = new TextField();
        pet.setPromptText(text("user.jobs.pet_name"));
        ComboBox<PetSpecies> species = new ComboBox<>();
        species.getItems().setAll(PetSpecies.values());
        species.setValue(PetSpecies.DOG);
        species.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(PetSpecies value) {
                return value == null ? "" : text("species." + value.databaseValue());
            }

            @Override
            public PetSpecies fromString(String value) {
                return null;
            }
        });
        DatePicker day = new DatePicker(LocalDate.now(Components.BAKU));
        ComboBox<LocalTime> time = new ComboBox<>();
        for (int minutes = 6 * 60; minutes <= 23 * 60; minutes += 30) {
            time.getItems().add(LocalTime.of(minutes / 60, minutes % 60));
        }
        time.setValue(LocalTime.now(Components.BAKU).plusHours(2).withMinute(0).withSecond(0).withNano(0));
        Spinner<Integer> minutes = new Spinner<>(WalkListingDetails.MIN_MINUTES, WalkListingDetails.MAX_MINUTES, 60, 15);
        TextField price = new TextField("10");
        ComboBox<String> city = new ComboBox<>();
        city.getItems().setAll(Pet.SUPPORTED_CITIES);
        city.setValue("Bakı");
        TextArea notes = new TextArea();
        notes.setPromptText(text("user.jobs.notes_hint"));
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, Components.label(text("user.jobs.pet_name"), "caption"), pet);
        form.addRow(1, Components.label(text("discover.species"), "caption"), species);
        form.addRow(2, Components.label(text("user.jobs.day"), "caption"), day);
        form.addRow(3, Components.label(text("user.jobs.time"), "caption"), time);
        form.addRow(4, Components.label(text("user.jobs.minutes"), "caption"), minutes);
        form.addRow(5, Components.label(text("walk.offer_price"), "caption"), price);
        form.addRow(6, Components.label(text("discover.city"), "caption"), city);
        form.addRow(7, Components.label(text("user.jobs.notes"), "caption"), notes);
        Label error = Components.wrapped("", "muted");
        dialog.getDialogPane().setContent(new VBox(10, form, error));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                details(pet, species, day, time, minutes, price, city, notes);
            } catch (RuntimeException exception) {
                error.setText(exception instanceof az.miyav.common.error.AppException app ? text(app.code()) : text("validation.walk_price"));
                event.consume();
            }
        });
        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(confirmed -> {
            WalkListingDetails details = details(pet, species, day, time, minutes, price, city, notes);
            context.load(() -> context.graph().walkListings().publish(context.session(), details), created -> {
                context.status(text("user.jobs.created"));
                tabs.getToggles().stream().filter(toggle -> "requests".equals(toggle.getUserData())).findFirst()
                        .ifPresent(toggle -> toggle.setSelected(true));
                refresh();
            });
        });
    }

    private static WalkListingDetails details(TextField pet, ComboBox<PetSpecies> species, DatePicker day, ComboBox<LocalTime> time,
                                              Spinner<Integer> minutes, TextField price, ComboBox<String> city, TextArea notes) {
        Instant startsAt = day.getValue().atTime(time.getValue()).atZone(Components.BAKU).toInstant();
        BigDecimal hourly = new BigDecimal(price.getText().trim().replace(',', '.'));
        return new WalkListingDetails(pet.getText(), species.getValue(), startsAt, minutes.getValue(),
                WalkListingDetails.requireValidPrice(hourly), city.getValue(), null, notes.getText() == null ? "" : notes.getText().trim());
    }

    private void confirm(String message, Runnable work, String done) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(context.window());
        alert.setHeaderText(null);
        alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(ok -> act(work, done));
    }

    private void act(Runnable work, String done) {
        context.load(() -> {
            work.run();
            return Boolean.TRUE;
        }, ok -> {
            context.status(text(done));
            refresh();
        });
    }

    private String name(PublicProfile profile) {
        return profile == null || profile.fullName().isBlank() ? text("user.jobs.unknown") : profile.fullName();
    }

    private static String rating(WalkerRating rating) {
        return rating == null ? "" : "   ★ " + rating.average().toPlainString() + " (" + rating.count() + ")";
    }

    private String duration(int minutes) {
        if (minutes % 60 == 0) {
            return text("walk.duration_hours", minutes / 60);
        }
        return minutes > 60 ? text("walk.duration_hours_minutes", minutes / 60, minutes % 60) : text("walk.duration_minutes", minutes);
    }

    private static String money(BigDecimal amount) {
        BigDecimal plain = amount.stripTrailingZeros();
        return (plain.scale() < 0 ? plain.setScale(0) : plain).toPlainString();
    }

    private record People(List<WalkOffer> offers, Map<UUID, PublicProfile> profiles, Map<UUID, WalkerRating> ratings) {
    }

    private final class JobCell extends ListCell<WalkListing> {
        @Override
        protected void updateItem(WalkListing listing, boolean empty) {
            super.updateItem(listing, empty);
            if (empty || listing == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label name = Components.label(Components.speciesGlyph(listing.details().species().databaseValue()) + "  "
                    + listing.details().petName(), "cell-title");
            Label meta = Components.label(Components.dateTime(listing.details().startsAt(), context.locale()) + " · "
                    + text("walk.per_hour", money(listing.effectivePricePerHour())), "muted");
            Label stage = Components.label(stage(listing), "caption");
            setGraphic(new VBox(2, name, meta, stage));
            setText(null);
        }
    }
}
