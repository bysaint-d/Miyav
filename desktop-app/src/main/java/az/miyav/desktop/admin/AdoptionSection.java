package az.miyav.desktop.admin;

import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetStatus;
import az.miyav.desktop.PhotoViewer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sahibləndirmə: every adoption listing as a photo card, with its publisher and applications. */
public final class AdoptionSection extends AdminSection {
    private final ToggleGroup filter = new ToggleGroup();
    private final FlowPane grid = new FlowPane(18, 18);
    private final VBox applications = new VBox(10);
    private List<Pet> listings = List.of();
    private Map<UUID, List<AdoptionApplication>> byListing = Map.of();
    private UUID focus;

    public AdoptionSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "adoption";
    }

    @Override
    public String icon() {
        return "🏡";
    }

    @Override
    public String titleKey() {
        return "console.adoption";
    }

    @Override
    public String subtitleKey() {
        return "console.adoption_subtitle";
    }

    @Override
    protected Node build() {
        javafx.scene.layout.HBox chips = Components.row(8,
                chip(text("console.filter_all"), null, true),
                chip(text("listing.status.active"), PetStatus.PUBLISHED, false),
                chip(text("listing.status.adopted"), PetStatus.ADOPTED, false),
                chip(text("listing.status.draft"), PetStatus.DRAFT, false),
                chip(text("listing.status.deleted"), PetStatus.ARCHIVED, false));
        return new VBox(18, chips, grid, Components.card(text("console.applications"), applications));
    }

    private ToggleButton chip(String label, PetStatus status, boolean selected) {
        ToggleButton chip = new ToggleButton(label);
        chip.getStyleClass().add("chip");
        chip.setUserData(status);
        chip.setToggleGroup(filter);
        chip.setSelected(selected);
        chip.setOnAction(event -> {
            if (!chip.isSelected()) {
                chip.setSelected(true);
            }
            render();
        });
        return chip;
    }

    @Override
    public void refresh() {
        context.members(members -> render());
        context.load(() -> context.graph().admin().listings(context.session()), list -> {
            listings = list;
            render();
        });
        context.load(() -> context.graph().admin().applications(context.session()), list -> {
            Map<UUID, List<AdoptionApplication>> grouped = new HashMap<>();
            for (AdoptionApplication application : list) {
                grouped.computeIfAbsent(application.petId(), key -> new ArrayList<>()).add(application);
            }
            byListing = grouped;
            renderApplications(list);
            render();
        });
    }

    @Override
    public void focus(UUID id) {
        focus = id;
        listings.stream().filter(pet -> pet.id().equals(id)).findFirst().ifPresent(this::openDetail);
    }

    private void render() {
        Object status = filter.getSelectedToggle() == null ? null : filter.getSelectedToggle().getUserData();
        grid.getChildren().clear();
        for (Pet pet : listings) {
            if (status == null || pet.status() == status) {
                grid.getChildren().add(card(pet));
            }
        }
        if (grid.getChildren().isEmpty()) {
            grid.getChildren().add(Components.empty("🏡", text("console.empty_adoption_title"), text("console.empty_adoption_body")));
        }
        if (focus != null) {
            UUID target = focus;
            focus = null;
            listings.stream().filter(pet -> pet.id().equals(target)).findFirst().ifPresent(this::openDetail);
        }
    }

    private Node card(Pet pet) {
        StackPane photo = photo(pet, 250, 190, false);
        Label name = Components.label(pet.name(), "card-title");
        Label meta = Components.label(speciesLine(pet), "caption");
        Label place = Components.label("📍 " + place(pet), "caption");
        String publisher = context.memberName(pet.ownerId());
        Label owner = Components.label(text("console.published_by", publisher.isEmpty() ? "—" : publisher), "caption");
        int pending = (int) byListing.getOrDefault(pet.id(), List.of()).stream()
                .filter(application -> application.status() == ApplicationStatus.PENDING).count();
        javafx.scene.layout.HBox badges = Components.row(6, Components.badge(statusText(pet.status()), tone(pet.status())));
        if (pending > 0) {
            badges.getChildren().add(Components.badge(text("console.pending_applications", pending), Components.Tone.WARNING));
        }
        VBox body = new VBox(6, name, meta, place, owner, badges);
        body.setPadding(new Insets(14, 16, 16, 16));
        VBox card = new VBox(photo, body);
        card.getStyleClass().add("photo-card");
        card.setPrefWidth(250);
        card.setOnMouseClicked(event -> openDetail(pet));
        return card;
    }

    /** Cards crop to fill the frame; the detail ({@code whole}) shows the entire photo and opens the viewer. */
    private StackPane photo(Pet pet, double width, double height, boolean whole) {
        Label glyph = Components.label(Components.speciesGlyph(pet.species().databaseValue()), "photo-glyph");
        StackPane frame = new StackPane(glyph);
        frame.getStyleClass().add("photo-placeholder");
        frame.setPrefSize(width, height);
        frame.setMinSize(width, height);
        frame.setMaxSize(width, height);
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(44);
        clip.setArcHeight(44);
        frame.setClip(clip);
        if (!pet.photoPaths().isEmpty()) {
            String path = pet.photoPaths().get(0);
            double edge = Math.max(width, height) * 2;
            context.load(() -> context.graph().discovery().photo(context.session(), path), bytes -> {
                var image = Components.image(bytes, edge, edge);
                if (image != null) {
                    ImageView view = whole ? Components.contain(image, width, height) : Components.cover(image, width, height);
                    frame.getChildren().setAll(view);
                    if (pet.photoPaths().size() > 1) {
                        Label count = Components.badge("📷 " + pet.photoPaths().size(), Components.Tone.NEUTRAL);
                        StackPane.setAlignment(count, Pos.BOTTOM_RIGHT);
                        StackPane.setMargin(count, new Insets(10));
                        frame.getChildren().add(count);
                    }
                }
            }, code -> {
            });
            if (whole) {
                frame.setCursor(Cursor.HAND);
                Tooltip.install(frame, new Tooltip(text("photo.open")));
                frame.setOnMouseClicked(event -> PhotoViewer.open(frame.getScene().getWindow(), pet.name(), pet.photoPaths(), 0,
                        photoPath -> context.graph().discovery().photo(context.session(), photoPath)));
            }
        }
        return frame;
    }

    private void openDetail(Pet pet) {
        Stage stage = new Stage();
        stage.initOwner(context.window());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(pet.name());
        VBox info = new VBox(10,
                Components.row(8, Components.badge(statusText(pet.status()), tone(pet.status()))),
                Components.label(pet.name(), "detail-title"),
                Components.label(speciesLine(pet), "muted"),
                Components.label("📍 " + place(pet), "muted"),
                Components.wrapped(pet.description()),
                Components.label(text("console.published_by", context.memberName(pet.ownerId())), "caption"),
                Components.label(text("console.application_count", byListing.getOrDefault(pet.id(), List.of()).size()), "caption"));
        Button owner = new Button(text("console.open_profile"));
        owner.getStyleClass().add("ghost");
        owner.setOnAction(event -> {
            stage.close();
            context.open("users", pet.ownerId());
        });
        info.getChildren().add(owner);
        if (pet.status() == PetStatus.PUBLISHED) {
            Button hide = new Button(text("admin.archive_listing"));
            hide.getStyleClass().add("danger");
            hide.setOnAction(event -> moderate(stage, () -> context.graph().admin().archiveListing(context.session(), pet.id(), "desktop")));
            info.getChildren().add(hide);
        } else if (pet.status() == PetStatus.ARCHIVED) {
            Button restore = new Button(text("admin.restore"));
            restore.getStyleClass().add("primary");
            restore.setOnAction(event -> moderate(stage, () -> context.graph().admin().restoreListing(context.session(), pet.id(), "desktop")));
            info.getChildren().add(restore);
        }
        VBox root = new VBox(18, photo(pet, 460, 340, true), info);
        root.getStyleClass().addAll("screen", "sheet");
        root.setPadding(new Insets(20));
        root.setPrefWidth(500);
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        Scene scene = new Scene(scroll, 520, 680);
        scene.getStylesheets().add(AdoptionSection.class.getResource("/az/miyav/desktop/miyav.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void moderate(Stage stage, Runnable action) {
        context.load(() -> {
            action.run();
            return Boolean.TRUE;
        }, done -> {
            stage.close();
            context.status(text("console.saved"));
            refresh();
        });
    }

    private void renderApplications(List<AdoptionApplication> list) {
        applications.getChildren().clear();
        if (list.isEmpty()) {
            applications.getChildren().add(Components.wrapped(text("empty.applications"), "muted"));
            return;
        }
        Map<UUID, String> names = new HashMap<>();
        for (Pet pet : listings) {
            names.put(pet.id(), pet.name());
        }
        for (AdoptionApplication application : list.subList(0, Math.min(12, list.size()))) {
            String applicant = context.memberName(application.applicantId());
            VBox lines = new VBox(2,
                    Components.label(text("console.application_line", applicant.isEmpty() ? "—" : applicant,
                            names.getOrDefault(application.petId(), "—")), "cell-title"),
                    Components.label(application.message(), "caption"));
            lines.setMaxWidth(640);
            applications.getChildren().add(Components.row(12, Components.avatar("✉", 34), lines, Components.spacer(),
                    Components.badge(text("application.status." + application.status().name().toLowerCase(java.util.Locale.ROOT)),
                            application.status() == ApplicationStatus.PENDING ? Components.Tone.WARNING
                                    : application.status() == ApplicationStatus.ACCEPTED ? Components.Tone.MINT : Components.Tone.NEUTRAL),
                    Components.label(Components.date(application.createdAt(), context.locale()), "caption")));
        }
        applications.setAlignment(Pos.TOP_LEFT);
    }

    private String speciesLine(Pet pet) {
        String breed = pet.breed().isEmpty() ? "" : " · " + pet.breed();
        int months = pet.ageMonths();
        String age = months >= 12 ? text("console.age_years", months / 12) : text("discover.months", months);
        return text("species." + pet.species().databaseValue()) + breed + " · " + age
                + " · " + text("gender." + pet.gender().databaseValue());
    }

    private static String place(Pet pet) {
        return pet.location() == null || pet.location().name().isEmpty() ? pet.city() : pet.location().name() + ", " + pet.city();
    }

    private String statusText(PetStatus status) {
        return text(switch (status) {
            case PUBLISHED -> "listing.status.active";
            case ADOPTED -> "listing.status.adopted";
            case ARCHIVED -> "listing.status.deleted";
            default -> "listing.status.draft";
        });
    }

    private static Components.Tone tone(PetStatus status) {
        return switch (status) {
            case PUBLISHED -> Components.Tone.MINT;
            case ADOPTED -> Components.Tone.SAGE;
            case ARCHIVED, REJECTED -> Components.Tone.DANGER;
            default -> Components.Tone.WARNING;
        };
    }
}
