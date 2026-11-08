package az.miyav.desktop.admin;

import az.miyav.domain.pet.PetProfile;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Members' own pets, the animals walking requests are made for. Care notes and contacts stay private. */
public final class PetsSection extends AdminSection {
    private final FlowPane grid = new FlowPane(18, 18);
    private final TextField search = new TextField();
    private List<PetProfile> pets = List.of();

    public PetsSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "pets";
    }

    @Override
    public String icon() {
        return "🐾";
    }

    @Override
    public String titleKey() {
        return "console.pets";
    }

    @Override
    public String subtitleKey() {
        return "console.pets_subtitle";
    }

    @Override
    protected Node build() {
        search.setPromptText(text("console.pets_search"));
        search.setMaxWidth(420);
        search.textProperty().addListener((observable, before, now) -> render());
        return new VBox(18, search, grid);
    }

    @Override
    public void refresh() {
        context.load(() -> context.insights().pets(context.session()), list -> {
            pets = list;
            render();
        });
    }

    private void render() {
        String needle = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        grid.getChildren().clear();
        for (PetProfile pet : pets) {
            if (needle.isEmpty() || pet.name().toLowerCase(Locale.ROOT).contains(needle)
                    || pet.breed().toLowerCase(Locale.ROOT).contains(needle)
                    || pet.ownerName().toLowerCase(Locale.ROOT).contains(needle)) {
                grid.getChildren().add(card(pet));
            }
        }
        if (grid.getChildren().isEmpty()) {
            grid.getChildren().add(Components.empty("🐾", text("console.empty_pets_title"),
                    pets.isEmpty() ? text("console.empty_pets_body") : text("console.empty_filter")));
        }
    }

    private Node card(PetProfile pet) {
        StackPane photo = new StackPane(Components.label(Components.speciesGlyph(pet.species().databaseValue()), "photo-glyph"));
        photo.getStyleClass().add("photo-placeholder");
        photo.setPrefSize(220, 130);
        Label name = Components.label(pet.name(), "card-title");
        String breed = pet.breed().isEmpty() ? "" : " · " + pet.breed();
        Label kind = Components.label(text("species." + pet.species().databaseValue()) + breed, "caption");
        Label facts = Components.label(facts(pet), "caption");
        Label owner = Components.label(text("console.pet_owner", pet.ownerName().isEmpty() ? "—" : pet.ownerName()), "caption");
        owner.setOnMouseClicked(event -> context.open("users", pet.ownerId()));
        owner.setStyle("-fx-cursor: hand; -fx-underline: true;");
        VBox body = new VBox(6, name, kind, facts, owner);
        body.setPadding(new Insets(14, 16, 16, 16));
        VBox card = new VBox(photo, body);
        card.getStyleClass().add("photo-card");
        card.setPrefWidth(220);
        return card;
    }

    private String facts(PetProfile pet) {
        String age = pet.ageYears() == null ? "—" : text("console.age_value", plain(pet.ageYears()));
        String weight = pet.weightKg() == null ? "—" : text("console.weight_value", plain(pet.weightKg()));
        return age + " · " + weight;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
