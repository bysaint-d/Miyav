package az.miyav.desktop.user;

import az.miyav.desktop.admin.Components;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.PublicProfile;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Adoption applications: those that came in for the member's listings, and those the member sent. */
public final class ApplicationsPage extends UserPage {
    private final ToggleButton incomingTab = new ToggleButton();
    private final ToggleButton mineTab = new ToggleButton();
    private final VBox list = new VBox(10);
    private Snapshot snapshot;

    public ApplicationsPage(UserContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "applications";
    }

    @Override
    public String titleKey() {
        return "user.nav.applications";
    }

    @Override
    public String icon() {
        return "📄";
    }

    @Override
    protected Node build() {
        ToggleGroup tabs = new ToggleGroup();
        for (ToggleButton tab : List.of(incomingTab, mineTab)) {
            tab.getStyleClass().add("chip");
            tab.setToggleGroup(tabs);
            tab.setOnAction(event -> {
                if (!tab.isSelected()) {
                    tab.setSelected(true);
                }
                render();
            });
        }
        incomingTab.setSelected(true);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return new VBox(12, Components.row(8, incomingTab, mineTab), scroll);
    }

    @Override
    public void refresh() {
        context.load(this::fetch, loaded -> {
            snapshot = loaded;
            render();
        });
    }

    private Snapshot fetch() {
        AuthSession session = context.session();
        Map<UUID, Pet> pets = new HashMap<>();
        List<AdoptionApplication> incoming = new ArrayList<>();
        for (Pet pet : context.graph().pets().mine(session)) {
            pets.put(pet.id(), pet);
            incoming.addAll(context.graph().adoptions().forPet(session, pet));
        }
        List<AdoptionApplication> mine = new ArrayList<>(context.graph().adoptions().mine(session));
        for (AdoptionApplication application : mine) {
            if (!pets.containsKey(application.petId())) {
                context.graph().discovery().listing(session, application.petId())
                        .ifPresent(pet -> pets.put(pet.id(), pet));
            }
        }
        Map<UUID, String> names = new HashMap<>();
        for (AdoptionApplication application : incoming) {
            names.computeIfAbsent(application.applicantId(), id -> context.graph().discovery().publisher(session, id)
                    .map(PublicProfile::fullName).orElse(""));
        }
        Comparator<AdoptionApplication> newest = Comparator.comparing(AdoptionApplication::createdAt).reversed();
        incoming.sort(Comparator.comparing((AdoptionApplication a) -> a.status() != ApplicationStatus.PENDING).thenComparing(newest));
        mine.sort(newest);
        return new Snapshot(incoming, mine, pets, names);
    }

    private void render() {
        if (snapshot == null) {
            return;
        }
        long pending = snapshot.incoming.stream().filter(a -> a.status() == ApplicationStatus.PENDING).count();
        incomingTab.setText(text("adoption.incoming") + (pending == 0 ? "" : " (" + pending + ")"));
        mineTab.setText(text("adoption.mine") + " (" + snapshot.mine.size() + ")");
        boolean incoming = incomingTab.isSelected();
        List<AdoptionApplication> rows = incoming ? snapshot.incoming : snapshot.mine;
        list.getChildren().clear();
        if (rows.isEmpty()) {
            list.getChildren().add(Components.empty("📄",
                    text(incoming ? "user.applications.incoming_empty" : "user.applications.mine_empty"), ""));
            return;
        }
        for (AdoptionApplication application : rows) {
            list.getChildren().add(card(application, incoming));
        }
    }

    private Node card(AdoptionApplication application, boolean incoming) {
        Pet pet = snapshot.pets.get(application.petId());
        String petName = pet == null ? "—" : pet.name();
        String title = incoming
                ? text("user.applications.from", nameOf(application.applicantId())) + " · " + petName
                : petName;
        HBox header = Components.row(8, Components.label(title, "cell-title"), Components.spacer(),
                status(application.status()),
                Components.label(Components.date(application.createdAt(), context.locale()), "caption"));
        VBox card = Components.card(header, Components.wrapped(application.message(), "muted"));
        HBox actions = Components.row(8);
        if (incoming && application.status() == ApplicationStatus.PENDING && pet != null) {
            Button accept = new Button(text("adoption.accept"));
            accept.getStyleClass().add("primary");
            accept.setOnAction(event -> accept(pet, application));
            Button reject = new Button(text("adoption.reject"));
            reject.getStyleClass().add("ghost");
            reject.setOnAction(event -> act(() -> context.graph().adoptions().reject(context.session(),
                    context.graph().users().current(context.session()), pet, application), "user.applications.rejected"));
            actions.getChildren().addAll(accept, reject);
        }
        if (!incoming && application.status() == ApplicationStatus.PENDING) {
            Button cancel = new Button(text("adoption.cancel"));
            cancel.getStyleClass().add("ghost");
            cancel.setOnAction(event -> act(() -> context.graph().adoptions().cancel(context.session(),
                    context.graph().users().current(context.session()), application), "user.applications.cancelled"));
            actions.getChildren().add(cancel);
        }
        if (!incoming && pet != null && application.status() != ApplicationStatus.CANCELLED) {
            Button write = new Button("💬 " + text("discover.write_owner"));
            write.getStyleClass().add("ghost");
            write.setOnAction(event -> context.load(() -> context.graph().messaging().openForAdoption(context.session(), pet),
                    room -> context.open("messages", room)));
            actions.getChildren().add(write);
        }
        if (!actions.getChildren().isEmpty()) {
            card.getChildren().add(actions);
        }
        if (application.status() == ApplicationStatus.PENDING) {
            card.getStyleClass().add("card-mint");
        }
        return card;
    }

    private void accept(Pet pet, AdoptionApplication application) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, text("user.applications.accept_confirm"),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(context.window());
        confirm.setHeaderText(pet.name());
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isPresent() && answer.get() == ButtonType.OK) {
            act(() -> context.graph().adoptions().accept(context.session(),
                    context.graph().users().current(context.session()), pet, application), "user.applications.accepted");
        }
    }

    private void act(java.util.function.Supplier<AdoptionApplication> action, String doneKey) {
        context.load(action, done -> {
            context.status(text(doneKey));
            refresh();
        });
    }

    private Node status(ApplicationStatus status) {
        Components.Tone tone = switch (status) {
            case PENDING -> Components.Tone.WARNING;
            case ACCEPTED -> Components.Tone.SAGE;
            case REJECTED -> Components.Tone.DANGER;
            default -> Components.Tone.NEUTRAL;
        };
        return Components.badge(text("application.status." + status.name().toLowerCase(Locale.ROOT)), tone);
    }

    private String nameOf(UUID applicantId) {
        String name = snapshot.names.getOrDefault(applicantId, "");
        return name.isBlank() ? text("user.messages.unknown") : name;
    }

    private record Snapshot(List<AdoptionApplication> incoming, List<AdoptionApplication> mine, Map<UUID, Pet> pets,
                            Map<UUID, String> names) {
    }
}
