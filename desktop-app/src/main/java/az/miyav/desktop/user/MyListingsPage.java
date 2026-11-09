package az.miyav.desktop.user;

import az.miyav.data.ListingStatusMapper;
import az.miyav.desktop.PhotoViewer;
import az.miyav.desktop.admin.Components;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.pet.Vaccination;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The member's adoption listings: create a draft, add up to five photos, publish, archive. The desktop has
 * no GPS, so a listing is placed at its city's centre, which is the same approximate precision the phone
 * app publishes.
 */
public final class MyListingsPage extends UserPage {
    private static final Map<String, double[]> CITY_CENTRES = Map.of(
            "Bakı", new double[]{40.4093, 49.8671},
            "Sumqayıt", new double[]{40.5897, 49.6686},
            "Abşeron", new double[]{40.4483, 49.7550});
    private final ListView<Pet> listings = new ListView<>();
    private final Map<String, Image> thumbs = new ConcurrentHashMap<>();
    private final Label editorTitle = Components.label("", "card-title");
    private final Label statusBadge = Components.badge("", Components.Tone.NEUTRAL);
    private final ComboBox<PetSpecies> species = new ComboBox<>();
    private final TextField name = new TextField();
    private final TextField breed = new TextField();
    private final Spinner<Integer> years = new Spinner<>(0, 30, 0);
    private final Spinner<Integer> months = new Spinner<>(0, 11, 0);
    private final ComboBox<PetGender> gender = new ComboBox<>();
    private final ComboBox<String> city = new ComboBox<>();
    private final ComboBox<Vaccination> vaccination = new ComboBox<>();
    private final TextArea description = new TextArea();
    private final FlowPane photos = new FlowPane(10, 10);
    private final Button addPhoto = new Button();
    private final Label photoHint = Components.wrapped("", "caption");
    private final Button save = new Button();
    private final Button publish = new Button();
    private final Button archive = new Button();
    private final Label editorStatus = Components.wrapped("", "muted");
    private VBox editor;
    private Pet current;

    public MyListingsPage(UserContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "listings";
    }

    @Override
    public String titleKey() {
        return "user.nav.listings";
    }

    @Override
    public String icon() {
        return "🐾";
    }

    @Override
    protected Node build() {
        Button create = new Button(text("user.listings.new"));
        create.getStyleClass().add("primary");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(event -> {
            listings.getSelectionModel().clearSelection();
            edit(null);
        });
        listings.setCellFactory(view -> new ListingCell());
        listings.setPlaceholder(Components.wrapped(text("user.listings.empty"), "muted"));
        listings.getSelectionModel().selectedItemProperty().addListener((observable, before, pet) -> {
            if (pet != null) {
                edit(pet);
            }
        });
        VBox.setVgrow(listings, javafx.scene.layout.Priority.ALWAYS);
        VBox left = new VBox(10, create, listings);
        left.setPrefWidth(320);

        species.getItems().setAll(PetSpecies.values());
        species.setConverter(converter(value -> text("species." + value.databaseValue())));
        gender.getItems().setAll(PetGender.MALE, PetGender.FEMALE, PetGender.UNKNOWN);
        gender.setConverter(converter(value -> text("gender." + value.databaseValue())));
        vaccination.getItems().setAll(Vaccination.values());
        vaccination.setConverter(converter(value -> text("vaccination." + value.databaseValue())));
        city.getItems().setAll("Bakı", "Sumqayıt", "Abşeron");
        years.setEditable(true);
        months.setEditable(true);
        years.setPrefWidth(90);
        months.setPrefWidth(90);
        description.setWrapText(true);
        description.setPrefRowCount(5);
        description.setPromptText(text("pet.description"));
        name.setPromptText(text("user.listings.name"));
        breed.setPromptText(text("pet.breed"));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.getColumnConstraints().setAll(ProfilePage.labelColumn(), new javafx.scene.layout.ColumnConstraints());
        int row = 0;
        form.addRow(row++, Components.label(text("user.listings.species"), "caption"), species);
        form.addRow(row++, Components.label(text("user.listings.name"), "caption"), name);
        form.addRow(row++, Components.label(text("pet.breed"), "caption"), breed);
        form.addRow(row++, Components.label(text("filters.age"), "caption"), Components.row(8, years,
                Components.label(text("user.listings.age_years"), "caption"), months,
                Components.label(text("user.listings.age_months"), "caption")));
        form.addRow(row++, Components.label(text("filters.gender"), "caption"), gender);
        form.addRow(row++, Components.label(text("discover.city"), "caption"), city);
        form.addRow(row++, Components.label(text("user.listings.vaccination"), "caption"), vaccination);
        form.add(Components.wrapped(text("user.listings.location_note"), "caption"), 1, row++);
        form.addRow(row, Components.label(text("pet.description"), "caption"), description);
        name.setPrefWidth(380);
        description.setPrefWidth(380);

        addPhoto.setText(text("user.listings.photo_add"));
        addPhoto.getStyleClass().add("ghost");
        addPhoto.setOnAction(event -> addPhoto());
        VBox photoBox = Components.card(text("user.listings.photos"), photos, Components.row(10, addPhoto, photoHint));

        save.setText(text("user.listings.save"));
        save.getStyleClass().add("primary");
        save.setOnAction(event -> save());
        publish.setText(text("pet.publish"));
        publish.getStyleClass().add("primary");
        publish.setOnAction(event -> run(() -> context.graph().pets().publish(context.session(), current), "user.listings.published"));
        archive.setText(text("pet.archive"));
        archive.getStyleClass().add("ghost");
        archive.setOnAction(event -> run(() -> context.graph().pets().archive(context.session(), current), "user.listings.archived"));

        editor = new VBox(14, Components.row(10, editorTitle, statusBadge), Components.card(form), photoBox,
                Components.row(10, save, publish, archive), editorStatus);
        editor.setPadding(new Insets(4, 8, 24, 8));
        ScrollPane right = new ScrollPane(editor);
        right.setFitToWidth(true);
        right.getStyleClass().add("detail-scroll");
        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.3);
        split.getStyleClass().add("split");
        split.setPrefHeight(640);
        edit(null);
        return split;
    }

    @Override
    public void refresh() {
        context.load(() -> context.graph().pets().mine(context.session()), mine -> {
            UUID keep = current == null ? null : current.id();
            listings.getItems().setAll(mine);
            if (keep != null) {
                mine.stream().filter(pet -> pet.id().equals(keep)).findFirst().ifPresent(pet -> {
                    listings.getSelectionModel().select(pet);
                    edit(pet);
                });
            }
        });
    }

    @Override
    public void focus(UUID id) {
        listings.getItems().stream().filter(pet -> pet.id().equals(id)).findFirst()
                .ifPresent(pet -> listings.getSelectionModel().select(pet));
    }

    private void edit(Pet pet) {
        current = pet;
        editorStatus.setText("");
        boolean fresh = pet == null;
        editorTitle.setText(fresh ? text("pet.create") : pet.name());
        statusBadge.setVisible(!fresh);
        species.setDisable(!fresh);
        if (fresh) {
            species.setValue(PetSpecies.DOG);
            name.clear();
            breed.clear();
            years.getValueFactory().setValue(0);
            months.getValueFactory().setValue(0);
            gender.setValue(PetGender.UNKNOWN);
            city.setValue("Bakı");
            vaccination.setValue(Vaccination.UNKNOWN);
            description.clear();
        } else {
            species.setValue(pet.species());
            name.setText(pet.name());
            breed.setText(pet.breed());
            years.getValueFactory().setValue(pet.ageMonths() / 12);
            months.getValueFactory().setValue(pet.ageMonths() % 12);
            gender.setValue(pet.gender());
            city.setValue(pet.city());
            vaccination.setValue(pet.vaccination());
            description.setText(pet.description());
            statusBadge.setText(statusText(pet.status()));
        }
        boolean editable = fresh || pet.status() == PetStatus.DRAFT || pet.status() == PetStatus.PUBLISHED
                || pet.status() == PetStatus.ARCHIVED;
        save.setDisable(!editable);
        publish.setVisible(!fresh && pet.status() != PetStatus.PUBLISHED && pet.status() != PetStatus.ADOPTED);
        publish.setManaged(publish.isVisible());
        archive.setVisible(!fresh && pet.status() == PetStatus.PUBLISHED);
        archive.setManaged(archive.isVisible());
        addPhoto.setDisable(fresh || pet.photoPaths().size() >= 5 || pet.status() == PetStatus.ADOPTED);
        photoHint.setText(fresh ? text("user.listings.photos_after_save") : "");
        renderPhotos();
    }

    private void renderPhotos() {
        photos.getChildren().clear();
        if (current == null) {
            return;
        }
        List<String> paths = current.photoPaths();
        for (int index = 0; index < paths.size(); index++) {
            photos.getChildren().add(photoTile(paths, index));
        }
    }

    private Node photoTile(List<String> paths, int index) {
        String path = paths.get(index);
        StackPane tile = new StackPane();
        tile.setMinSize(120, 120);
        tile.setMaxSize(120, 120);
        tile.getStyleClass().add("photo-card");
        tile.setCursor(Cursor.HAND);
        Function<String, byte[]> source = item -> context.graph().pets().photo(context.session(), item);
        tile.setOnMouseClicked(event -> PhotoViewer.open(context.window(), current.name(), paths, index, source));
        Image cached = thumbs.get(path);
        if (cached != null) {
            tile.getChildren().add(Components.cover(cached, 120, 120));
        } else {
            tile.getChildren().add(Components.label("🐾", "photo-glyph"));
            context.load(() -> Components.image(source.apply(path), 360, 360), image -> {
                if (image != null) {
                    thumbs.put(path, image);
                    tile.getChildren().set(0, Components.cover(image, 120, 120));
                }
            }, code -> {
            });
        }
        if (current.status() != PetStatus.ADOPTED) {
            Button remove = new Button("×");
            remove.getStyleClass().add("danger");
            StackPane.setAlignment(remove, Pos.TOP_RIGHT);
            remove.setOnAction(event -> {
                event.consume();
                Pet pet = current;
                run(() -> context.graph().pets().removePhoto(context.session(), pet, path), "user.listings.saved");
            });
            tile.getChildren().add(remove);
        }
        return tile;
    }

    private void save() {
        Pet existing = current;
        PetSpecies chosenSpecies = species.getValue();
        String chosenName = name.getText();
        String chosenBreed = breed.getText();
        int age = years.getValue() * 12 + months.getValue();
        PetGender chosenGender = gender.getValue();
        String chosenCity = city.getValue();
        Vaccination chosenVaccination = vaccination.getValue();
        String text = description.getText();
        editorStatus.setText("…");
        context.load(() -> {
            ListingLocation location = locationFor(existing, chosenCity);
            if (existing == null) {
                return context.graph().pets().createDraft(context.session(), chosenSpecies, chosenName, chosenBreed, age,
                        chosenGender, chosenCity, text, chosenVaccination, location);
            }
            return context.graph().pets().update(context.session(), existing, chosenName, chosenBreed, age, chosenGender,
                    chosenCity, text, existing.status(), chosenVaccination, location);
        }, saved -> done(saved, "user.listings.saved"), code -> editorStatus.setText(text(code)));
    }

    private static ListingLocation locationFor(Pet existing, String city) {
        if (existing != null && existing.location() != null && city.equals(existing.city())) {
            return existing.location();
        }
        double[] centre = CITY_CENTRES.getOrDefault(city, CITY_CENTRES.get("Bakı"));
        return new ListingLocation(centre[0], centre[1], city);
    }

    private void addPhoto() {
        Pet pet = current;
        if (pet == null) {
            return;
        }
        File file = Jpeg.pick(context.window(), text("image.files"));
        if (file == null) {
            return;
        }
        editorStatus.setText("…");
        context.load(() -> context.graph().pets().addPhoto(context.session(), pet, Jpeg.encode(file, 1600, Jpeg.LISTING_BYTES)),
                saved -> done(saved, "user.listings.saved"), code -> editorStatus.setText(text(code)));
    }

    private void run(java.util.function.Supplier<Pet> action, String doneKey) {
        editorStatus.setText("…");
        context.load(action, saved -> done(saved, doneKey), code -> editorStatus.setText(text(code)));
    }

    private void done(Pet saved, String messageKey) {
        current = saved;
        boolean listed = false;
        for (int index = 0; index < listings.getItems().size(); index++) {
            if (listings.getItems().get(index).id().equals(saved.id())) {
                listings.getItems().set(index, saved);
                listed = true;
            }
        }
        if (!listed) {
            listings.getItems().add(0, saved);
        }
        listings.getSelectionModel().select(saved);
        edit(saved);
        editorStatus.setText(text(messageKey));
    }

    private String statusText(PetStatus status) {
        try {
            return text("listing.status." + ListingStatusMapper.toDatabase(status));
        } catch (RuntimeException exception) {
            return status.name().toLowerCase(Locale.ROOT);
        }
    }

    private static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private final class ListingCell extends ListCell<Pet> {
        private final StackPane thumb = new StackPane();
        private final Label title = Components.label("", "cell-title");
        private final Label meta = Components.label("", "muted");
        private final Label badge = Components.badge("", Components.Tone.NEUTRAL);
        private final Node root;

        ListingCell() {
            thumb.setMinSize(56, 56);
            thumb.setMaxSize(56, 56);
            thumb.getStyleClass().add("photo-card");
            root = Components.row(12, thumb, new VBox(4, title, meta, badge));
        }

        @Override
        protected void updateItem(Pet pet, boolean empty) {
            super.updateItem(pet, empty);
            if (empty || pet == null) {
                setGraphic(null);
                return;
            }
            title.setText(pet.name());
            meta.setText(text("species." + pet.species().databaseValue()) + " · " + pet.city());
            badge.setText(statusText(pet.status()));
            badge.getStyleClass().removeAll("badge-sage", "badge-warning", "badge-neutral", "badge-peach");
            badge.getStyleClass().add(switch (pet.status()) {
                case PUBLISHED -> "badge-sage";
                case DRAFT -> "badge-warning";
                case ADOPTED -> "badge-peach";
                default -> "badge-neutral";
            });
            thumb.getChildren().setAll(Components.label(Components.speciesGlyph(pet.species().databaseValue()), "nav-icon"));
            if (!pet.photoPaths().isEmpty()) {
                String path = pet.photoPaths().get(0);
                UUID id = pet.id();
                Image cached = thumbs.get(path);
                if (cached != null) {
                    thumb.getChildren().setAll(Components.cover(cached, 56, 56));
                } else {
                    context.load(() -> Components.image(context.graph().pets().photo(context.session(), path), 360, 360), image -> {
                        if (image != null) {
                            thumbs.put(path, image);
                            if (getItem() != null && getItem().id().equals(id)) {
                                thumb.getChildren().setAll(Components.cover(image, 56, 56));
                            }
                        }
                    }, code -> {
                    });
                }
            }
            setGraphic(root);
        }
    }
}
