package az.miyav.desktop;

import az.miyav.common.error.AppException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.desktop.admin.AdminSection;
import az.miyav.desktop.admin.AdoptionSection;
import az.miyav.desktop.admin.Components;
import az.miyav.desktop.admin.ConsoleContext;
import az.miyav.desktop.admin.MessagesSection;
import az.miyav.desktop.admin.NearMeSection;
import az.miyav.desktop.admin.NotificationsSection;
import az.miyav.desktop.admin.OverviewSection;
import az.miyav.desktop.admin.PetsSection;
import az.miyav.desktop.admin.PlacesSection;
import az.miyav.desktop.admin.ReportsSection;
import az.miyav.desktop.admin.SettingsSection;
import az.miyav.desktop.admin.UsersSection;
import az.miyav.desktop.admin.WalkingSection;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.security.AccessPolicy;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The admin console shell: a sidebar of {@link AdminSection}s and the selected section's page. The shell
 * only knows the abstract section type; each section loads its own data from the shared services.
 */
public final class HomeController {
    private final MiyavDesktopApp app;
    private final Map<String, AdminSection> sections = new LinkedHashMap<>();
    private final Map<String, ToggleButton> navigation = new LinkedHashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();
    private AdminSection current;
    @FXML private VBox sidebar;
    @FXML private Label title;
    @FXML private Label subtitle;
    @FXML private Button refreshButton;
    @FXML private ScrollPane scroll;
    @FXML private StackPane content;
    @FXML private Label status;

    public HomeController(MiyavDesktopApp app) {
        this.app = app;
    }

    @FXML
    private void initialize() {
        DesktopGraph graph = app.graph();
        refreshButton.setText("↻  " + graph.text("console.refresh"));
        if (graph.session().decide(ProtectedAction.ADMIN) != AccessPolicy.Decision.ALLOWED) {
            showDenied();
            return;
        }
        ConsoleContext context = new ConsoleContext(graph, graph.insights(), status::setText, this::open,
                this::sessionLost, () -> content.getScene().getWindow());
        register(new OverviewSection(context), new WalkingSection(context), new NearMeSection(context),
                new AdoptionSection(context), new PetsSection(context), new UsersSection(context),
                new PlacesSection(context),
                new MessagesSection(context), new ReportsSection(context), new NotificationsSection(context),
                new SettingsSection(context, this::selectLocale, this::signOut, app::showDiscover));
        buildSidebar();
        open("overview", null);
    }

    private void register(AdminSection... list) {
        for (AdminSection section : list) {
            sections.put(section.id(), section);
        }
    }

    private void buildSidebar() {
        DesktopGraph graph = app.graph();
        Label wordmark = Components.label("MIYAV.", "wordmark");
        Label tagline = Components.wrapped(graph.text("console.tagline"), "caption");
        VBox brand = new VBox(2, wordmark, tagline);
        brand.setStyle("-fx-padding: 0 8 18 12;");
        sidebar.getChildren().add(brand);
        group("console.group_community", List.of("overview", "walking", "nearme"));
        group("console.group_content", List.of("adoption", "pets", "users", "places"));
        group("console.group_safety", List.of("messages", "reports", "notifications"));
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().addAll(spacer, item(sections.get("settings")));
        String email = graph.session().session().map(session -> session.email()).orElse("");
        VBox admin = new VBox(2, Components.label(graph.text("console.signed_in_as"), "caption"), Components.label(email, "cell-title"));
        admin.getStyleClass().addAll("card", "card-mint");
        admin.setStyle("-fx-padding: 12 14 12 14;");
        sidebar.getChildren().add(admin);
    }

    private void group(String key, List<String> ids) {
        java.util.Locale locale = java.util.Locale.forLanguageTag(app.graph().catalog().locale().tag());
        sidebar.getChildren().add(Components.label(app.graph().text(key).toUpperCase(locale), "nav-section"));
        for (String id : ids) {
            sidebar.getChildren().add(item(sections.get(id)));
        }
    }

    private ToggleButton item(AdminSection section) {
        Label icon = Components.label(section.icon(), "nav-icon");
        ToggleButton button = new ToggleButton(app.graph().text(section.titleKey()), icon);
        button.getStyleClass().add("nav-item");
        button.setToggleGroup(navGroup);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMnemonicParsing(false);
        button.setOnAction(event -> open(section.id(), null));
        navigation.put(section.id(), button);
        return button;
    }

    /** Opens a section, optionally focused on one record (a member, a listing). */
    private void open(String id, UUID focus) {
        AdminSection section = sections.get(id);
        if (section == null) {
            return;
        }
        navigation.get(id).setSelected(true);
        if (current != section) {
            current = section;
            title.setText(app.graph().text(section.titleKey()));
            subtitle.setText(app.graph().text(section.subtitleKey()));
            content.getChildren().setAll(section.view());
            scroll.setVvalue(0);
            status.setText("");
            section.refresh();
        }
        if (focus != null) {
            section.focus(focus);
        }
    }

    @FXML
    private void onRefresh() {
        if (current != null) {
            status.setText("");
            current.refresh();
        }
    }

    private void selectLocale(LocaleCode locale) {
        app.selectLocale(locale);
    }

    private void signOut() {
        try {
            app.graph().session().signOut();
        } catch (AppException exception) {
            status.setText(app.graph().text(exception.code()));
        }
        app.showDiscover();
    }

    private void sessionLost() {
        app.graph().session().expire();
        app.showDiscover();
    }

    private void showDenied() {
        title.setText(app.graph().text("console.title"));
        subtitle.setText("");
        refreshButton.setVisible(false);
        Button back = new Button(app.graph().text("discover.back"));
        back.getStyleClass().add("primary");
        back.setOnAction(event -> app.showDiscover());
        content.getChildren().setAll(Components.card(Components.empty("🔒", app.graph().text("admin.denied"),
                app.graph().text("guest.action.admin")), back));
        sidebar.getChildren().setAll(Components.label("MIYAV.", "wordmark"));
    }
}
