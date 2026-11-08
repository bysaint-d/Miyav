package az.miyav.desktop;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.AppException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.admin.AdminStats;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.moderation.Report;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class HomeController {
    private enum Panel { USERS, LISTINGS, APPLICATIONS, REPORTS }

    private final MiyavDesktopApp app;
    private Panel panel = Panel.USERS;
    private final List<UUID> ids = new ArrayList<>();
    @FXML private Label title;
    @FXML private Label stats;
    @FXML private Label empty;
    @FXML private ListView<String> rows;
    @FXML private Button logoutButton;
    @FXML private Button usersButton;
    @FXML private Button listingsButton;
    @FXML private Button applicationsButton;
    @FXML private Button reportsButton;
    @FXML private Button suspendButton;
    @FXML private Button archiveButton;
    @FXML private Button restoreButton;
    @FXML private Button resolveButton;
    @FXML private Label message;
    @FXML private Button az;
    @FXML private Button en;
    @FXML private Button ru;

    public HomeController(MiyavDesktopApp app) {
        this.app = app;
    }

    @FXML
    private void initialize() {
        localize();
        AuthSession session = app.graph().sessions().load().orElse(null);
        boolean admin = session != null && session.role() == UserRole.ADMIN;
        if (!admin) {
            empty.setText(app.graph().catalog().text("admin.denied"));
            setAdminControls(false);
            return;
        }
        setAdminControls(true);
        refreshStats();
        showUsers();
    }

    @FXML
    private void onLogout() {
        try {
            app.graph().auth().logout();
        } catch (AppException exception) {
            message.setText(app.graph().catalog().text(exception.code()));
        }
        app.showLogin();
    }

    @FXML
    private void showUsers() {
        panel = Panel.USERS;
        load(() -> {
            List<User> users = app.graph().admin().users(session());
            List<String> labels = new ArrayList<>();
            ids.clear();
            for (User user : users) {
                ids.add(user.id());
                labels.add(user.fullName() + " · " + user.city() + " · " + user.role() + " · " + user.suspended());
            }
            return labels;
        });
    }

    @FXML
    private void showListings() {
        panel = Panel.LISTINGS;
        load(() -> {
            List<Pet> listings = app.graph().admin().listings(session());
            List<String> labels = new ArrayList<>();
            ids.clear();
            for (Pet pet : listings) {
                ids.add(pet.id());
                labels.add(pet.name() + " · " + pet.city() + " · " + pet.status());
            }
            return labels;
        });
    }

    @FXML
    private void showApplications() {
        panel = Panel.APPLICATIONS;
        load(() -> {
            List<AdoptionApplication> applications = app.graph().admin().applications(session());
            List<String> labels = new ArrayList<>();
            ids.clear();
            for (AdoptionApplication application : applications) {
                ids.add(application.id());
                labels.add(application.status() + " · " + application.message());
            }
            return labels;
        });
    }

    @FXML
    private void showReports() {
        panel = Panel.REPORTS;
        load(() -> {
            List<Report> reports = app.graph().admin().reports(session());
            List<String> labels = new ArrayList<>();
            ids.clear();
            for (Report report : reports) {
                ids.add(report.id());
                labels.add((report.resolved() ? "resolved" : "open") + " · " + report.reason());
            }
            return labels;
        });
    }

    @FXML
    private void onSuspend() {
        UUID id = selected();
        if (id == null || panel != Panel.USERS) {
            return;
        }
        run(() -> {
            app.graph().admin().suspend(session(), id, true, "desktop");
            return null;
        }, ignored -> showUsers());
    }

    @FXML
    private void onArchive() {
        UUID id = selected();
        if (id == null || panel != Panel.LISTINGS) {
            return;
        }
        run(() -> {
            app.graph().admin().archiveListing(session(), id, "desktop");
            return null;
        }, ignored -> showListings());
    }

    @FXML
    private void onRestore() {
        UUID id = selected();
        if (id == null || panel != Panel.LISTINGS) {
            return;
        }
        run(() -> {
            app.graph().admin().restoreListing(session(), id, "desktop");
            return null;
        }, ignored -> showListings());
    }

    @FXML
    private void onResolve() {
        UUID id = selected();
        if (id == null || panel != Panel.REPORTS) {
            return;
        }
        run(() -> {
            app.graph().admin().resolveReport(session(), id, "desktop");
            return null;
        }, ignored -> showReports());
    }

    @FXML
    private void useAz() {
        choose(LocaleCode.AZ);
    }

    @FXML
    private void useEn() {
        choose(LocaleCode.EN);
    }

    @FXML
    private void useRu() {
        choose(LocaleCode.RU);
    }

    private void choose(LocaleCode locale) {
        AuthSession session = app.graph().sessions().load().orElse(null);
        if (session == null) {
            app.selectLocale(locale);
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                app.graph().users().updateLocale(session, locale);
                Platform.runLater(() -> app.selectLocale(locale));
            } catch (AppException exception) {
                Platform.runLater(() -> message.setText(app.graph().catalog().text(exception.code())));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void localize() {
        var catalog = app.graph().catalog();
        title.setText(catalog.text("admin.dashboard"));
        logoutButton.setText(catalog.text("auth.logout"));
        usersButton.setText(catalog.text("admin.users"));
        listingsButton.setText(catalog.text("admin.listings"));
        applicationsButton.setText(catalog.text("admin.applications"));
        reportsButton.setText(catalog.text("admin.reports"));
        suspendButton.setText(catalog.text("admin.suspend"));
        archiveButton.setText(catalog.text("admin.archive_listing"));
        restoreButton.setText(catalog.text("admin.restore"));
        resolveButton.setText(catalog.text("admin.resolve"));
        az.setText(catalog.text("language.az"));
        en.setText(catalog.text("language.en"));
        ru.setText(catalog.text("language.ru"));
    }

    private void refreshStats() {
        run(() -> app.graph().admin().stats(session()), value -> {
            AdminStats numbers = (AdminStats) value;
            stats.setText(app.graph().catalog().text("stats.users") + ": " + numbers.users()
                    + "  ·  " + app.graph().catalog().text("stats.listings") + ": " + numbers.listings()
                    + "  ·  " + app.graph().catalog().text("stats.applications") + ": " + numbers.pendingApplications()
                    + "  ·  " + app.graph().catalog().text("stats.reports") + ": " + numbers.openReports());
        });
    }

    private void load(Supplier<List<String>> loader) {
        run(loader, value -> {
            @SuppressWarnings("unchecked")
            List<String> labels = (List<String>) value;
            if (labels.isEmpty()) {
                empty.setText(app.graph().catalog().text("home.empty"));
                rows.getItems().clear();
                return;
            }
            empty.setText("");
            rows.getItems().setAll(labels);
        });
    }

    private void run(Supplier<?> work, java.util.function.Consumer<Object> ok) {
        Thread worker = new Thread(() -> {
            try {
                Object result = work.get();
                Platform.runLater(() -> ok.accept(result));
            } catch (AccessDeniedException exception) {
                Platform.runLater(() -> empty.setText(app.graph().catalog().text(exception.code())));
            } catch (AppException exception) {
                Platform.runLater(() -> message.setText(app.graph().catalog().text(exception.code())));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private UUID selected() {
        int index = rows.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= ids.size()) {
            return null;
        }
        return ids.get(index);
    }

    private AuthSession session() {
        return app.graph().auth().requireSession();
    }

    private void setAdminControls(boolean visible) {
        usersButton.setVisible(visible);
        listingsButton.setVisible(visible);
        applicationsButton.setVisible(visible);
        reportsButton.setVisible(visible);
        suspendButton.setVisible(visible);
        archiveButton.setVisible(visible);
        restoreButton.setVisible(visible);
        resolveButton.setVisible(visible);
        rows.setVisible(visible);
    }
}
