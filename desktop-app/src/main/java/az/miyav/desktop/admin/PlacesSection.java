package az.miyav.desktop.admin;

import az.miyav.domain.place.Place;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vets and pet shops shown on the phone's Vets tab. Admins add and correct places, mark them verified, and
 * hide ones that closed or are not what they claim to be.
 */
public final class PlacesSection extends AdminSection {
    private static final Pattern COORDINATES = Pattern.compile("^\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,; ]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$");
    private final ListView<Place> list = new ListView<>();
    private final TextField search = new TextField();
    private final ComboBox<Place.Kind> kindFilter = new ComboBox<>();
    private final CheckBox showHidden = new CheckBox();
    private final Label formTitle = Components.label("", "card-title");
    private final ComboBox<Place.Kind> kind = new ComboBox<>();
    private final TextField name = new TextField();
    private final TextField address = new TextField();
    private final TextField phone = new TextField();
    private final TextField whatsapp = new TextField();
    private final TextField hours = new TextField();
    private final TextField coordinates = new TextField();
    private final CheckBox allDay = new CheckBox();
    private final CheckBox verified = new CheckBox();
    private final CheckBox active = new CheckBox();
    private final CheckBox sponsored = new CheckBox();
    private final TextField website = new TextField();
    private final TextField description = new TextField();
    private final Label formStatus = Components.wrapped("", "muted");
    private List<Place> places = List.of();
    private Place editing;

    public PlacesSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "places";
    }

    @Override
    public String icon() {
        return "✚";
    }

    @Override
    public String titleKey() {
        return "console.places";
    }

    @Override
    public String subtitleKey() {
        return "console.places_subtitle";
    }

    @Override
    protected Node build() {
        StringConverter<Place.Kind> kinds = new StringConverter<>() {
            @Override
            public String toString(Place.Kind value) {
                return value == null ? text("filter.all") : text("places.kind." + value.databaseValue());
            }

            @Override
            public Place.Kind fromString(String text) {
                return null;
            }
        };
        search.setPromptText(text("console.places_search"));
        search.textProperty().addListener((observable, before, now) -> render());
        kindFilter.getItems().setAll(null, Place.Kind.VET, Place.Kind.PET_SHOP);
        kindFilter.setConverter(kinds);
        kindFilter.getSelectionModel().select(Place.Kind.VET);
        kindFilter.setOnAction(event -> render());
        showHidden.setText(text("console.places_show_hidden"));
        showHidden.setOnAction(event -> render());
        Button create = new Button(text("console.places_new"));
        create.getStyleClass().add("primary");
        create.setOnAction(event -> {
            list.getSelectionModel().clearSelection();
            edit(null);
        });
        list.setCellFactory(view -> new PlaceCell());
        list.getSelectionModel().selectedItemProperty().addListener((observable, before, place) -> {
            if (place != null) {
                edit(place);
            }
        });
        list.setPrefHeight(560);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox left = new VBox(10, Components.row(8, search, kindFilter), Components.row(8, showHidden, Components.spacer(), create), list);

        kind.getItems().setAll(Place.Kind.VET, Place.Kind.PET_SHOP);
        kind.setConverter(kinds);
        hours.setPromptText("Mo-Fr 09:00-19:00; Sa 10:00-16:00");
        coordinates.setPromptText("40.40930, 49.86710");
        phone.setPromptText("+994 12 345 67 89");
        whatsapp.setPromptText("994501234567 · " + text("places.whatsapp_hint"));
        allDay.setText(text("places.24h"));
        verified.setText(text("places.verified"));
        active.setText(text("places.active"));
        sponsored.setText(text("places.sponsored"));
        website.setPromptText("https://");
        description.setPromptText(text("places.description_hint"));
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(140);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().setAll(labels, fields);
        int row = 0;
        form.addRow(row++, Components.label(text("places.kind"), "caption"), kind);
        form.addRow(row++, Components.label(text("places.name"), "caption"), name);
        form.addRow(row++, Components.label(text("places.address"), "caption"), address);
        form.addRow(row++, Components.label(text("places.phone"), "caption"), phone);
        form.addRow(row++, Components.label(text("places.whatsapp"), "caption"), whatsapp);
        form.addRow(row++, Components.label(text("places.hours"), "caption"), hours);
        form.add(Components.wrapped(text("places.hours_hint"), "caption"), 1, row++);
        form.addRow(row++, Components.label(text("places.coordinates"), "caption"), coordinates);
        form.add(Components.wrapped(text("places.coordinates_hint"), "caption"), 1, row++);
        form.addRow(row++, Components.label(text("places.website"), "caption"), website);
        form.addRow(row++, Components.label(text("places.description"), "caption"), description);
        form.add(new VBox(8, allDay, verified, active, sponsored), 1, row);
        Button save = new Button(text("profile.save"));
        save.getStyleClass().add("primary");
        save.setOnAction(event -> save());
        VBox editor = Components.card(formTitle, form, Components.row(12, save, formStatus));

        SplitPane split = new SplitPane(Components.card(left), editor);
        split.setDividerPositions(0.42);
        split.getStyleClass().add("split");
        edit(null);
        return split;
    }

    @Override
    public void refresh() {
        context.load(() -> context.graph().places().all(context.session()), loaded -> {
            places = loaded;
            render();
        });
    }

    private void render() {
        String needle = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        Place.Kind wanted = kindFilter.getValue();
        UUID keep = editing == null ? null : editing.id();
        list.getItems().setAll(places.stream()
                .filter(place -> wanted == null || place.kind() == wanted)
                .filter(place -> showHidden.isSelected() || place.active())
                .filter(place -> needle.isEmpty() || place.name().toLowerCase(Locale.ROOT).contains(needle)
                        || place.address().orElse("").toLowerCase(Locale.ROOT).contains(needle))
                .toList());
        list.setPlaceholder(Components.wrapped(text("console.empty_filter"), "muted"));
        if (keep != null) {
            list.getItems().stream().filter(place -> place.id().equals(keep)).findFirst()
                    .ifPresent(place -> list.getSelectionModel().select(place));
        }
    }

    private void edit(Place place) {
        editing = place;
        formStatus.setText("");
        formTitle.setText(place == null ? text("console.places_new") : place.name());
        kind.setValue(place == null ? (kindFilter.getValue() == null ? Place.Kind.VET : kindFilter.getValue()) : place.kind());
        name.setText(place == null ? "" : place.name());
        address.setText(place == null ? "" : place.address().orElse(""));
        phone.setText(place == null ? "" : place.phone().orElse(""));
        whatsapp.setText(place == null ? "" : place.whatsapp().orElse(""));
        hours.setText(place == null ? "" : place.openingHours().orElse(""));
        coordinates.setText(place == null ? "" : String.format(Locale.ROOT, "%.5f, %.5f", place.latitude(), place.longitude()));
        allDay.setSelected(place != null && place.open24h());
        verified.setSelected(place != null && place.verified());
        active.setSelected(place == null || place.active());
        sponsored.setSelected(place != null && place.sponsored());
        website.setText(place == null ? "" : place.website().orElse(""));
        description.setText(place == null ? "" : place.description().orElse(""));
    }

    private void save() {
        Matcher point = COORDINATES.matcher(coordinates.getText() == null ? "" : coordinates.getText());
        if (!point.matches()) {
            formStatus.setText(text("places.coordinates_invalid"));
            return;
        }
        Place place;
        try {
            place = new Place(editing == null ? UUID.randomUUID() : editing.id(), kind.getValue(), name.getText().trim(),
                    address.getText(), phone.getText(), hours.getText(),
                    Double.parseDouble(point.group(1)), Double.parseDouble(point.group(2)),
                    allDay.isSelected(), whatsapp.getText().replaceAll("[^0-9]", ""), verified.isSelected(), active.isSelected(),
                    sponsored.isSelected(), website.getText(), description.getText());
        } catch (RuntimeException exception) {
            formStatus.setText(text("place.name"));
            return;
        }
        formStatus.setText("…");
        context.load(() -> context.graph().places().save(context.session(), place), saved -> {
            editing = saved;
            places = new java.util.ArrayList<>(places.stream().filter(item -> !item.id().equals(saved.id())).toList());
            places.add(saved);
            places.sort(java.util.Comparator.comparing(Place::name, String.CASE_INSENSITIVE_ORDER));
            render();
            edit(saved);
            formStatus.setText(text("places.saved"));
        }, code -> formStatus.setText(text(code)));
    }

    private final class PlaceCell extends ListCell<Place> {
        @Override
        protected void updateItem(Place place, boolean empty) {
            super.updateItem(place, empty);
            if (empty || place == null) {
                setGraphic(null);
                return;
            }
            Label title = Components.label((place.kind() == Place.Kind.VET ? "✚  " : "🐾  ") + place.name(), "cell-title");
            String meta = place.address().orElse(text("places.no_address"))
                    + place.phone().map(number -> " · " + number).orElse("");
            VBox box = new VBox(3, title, Components.label(meta, "muted"));
            var badges = Components.row(6);
            if (!place.active()) {
                badges.getChildren().add(Components.badge(text("places.hidden"), Components.Tone.NEUTRAL));
            }
            if (place.sponsored()) {
                badges.getChildren().add(Components.badge("★ " + text("places.sponsored"), Components.Tone.WARNING));
            }
            if (place.verified()) {
                badges.getChildren().add(Components.badge(text("places.verified"), Components.Tone.SAGE));
            }
            if (place.open24h()) {
                badges.getChildren().add(Components.badge("24/7", Components.Tone.PEACH));
            }
            if (!badges.getChildren().isEmpty()) {
                box.getChildren().add(badges);
            }
            setGraphic(box);
        }
    }
}
