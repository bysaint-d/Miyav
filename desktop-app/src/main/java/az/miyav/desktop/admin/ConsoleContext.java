package az.miyav.desktop.admin;

import az.miyav.application.AdminInsightsService;
import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.AppException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.admin.UserSummary;
import az.miyav.domain.auth.AuthSession;
import az.miyav.desktop.DesktopGraph;
import javafx.application.Platform;
import javafx.stage.Window;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What every console section needs: services, translations, background loading, navigation and a
 * shared directory of members (so names can be shown on listings, reports and map previews).
 */
public final class ConsoleContext {
    private final DesktopGraph graph;
    private final AdminInsightsService insights;
    private final Consumer<String> status;
    private final BiConsumer<String, UUID> navigator;
    private final Runnable sessionLost;
    private final Supplier<Window> window;
    private volatile Map<UUID, UserSummary> members = Map.of();

    public ConsoleContext(DesktopGraph graph, AdminInsightsService insights, Consumer<String> status,
                          BiConsumer<String, UUID> navigator, Runnable sessionLost, Supplier<Window> window) {
        this.graph = graph;
        this.insights = insights;
        this.status = status;
        this.navigator = navigator;
        this.sessionLost = sessionLost;
        this.window = window;
    }

    public DesktopGraph graph() {
        return graph;
    }

    public AdminInsightsService insights() {
        return insights;
    }

    public AuthSession session() {
        return graph.session().requireSession();
    }

    public Instant now() {
        return Instant.now();
    }

    public String text(String key, Object... args) {
        return graph.text(key, args);
    }

    public Locale locale() {
        LocaleCode code = graph.catalog().locale();
        return Locale.forLanguageTag(code.tag());
    }

    public Window window() {
        return window.get();
    }

    public void open(String sectionId, UUID focus) {
        navigator.accept(sectionId, focus);
    }

    public void status(String message) {
        status.accept(message);
    }

    /** Runs {@code work} off the UI thread and hands the result back on it. Errors go to the status line. */
    public <T> void load(Supplier<T> work, Consumer<T> ok) {
        load(work, ok, code -> status(text(code)));
    }

    public <T> void load(Supplier<T> work, Consumer<T> ok, Consumer<String> failed) {
        Thread worker = new Thread(() -> {
            try {
                T result = work.get();
                Platform.runLater(() -> ok.accept(result));
            } catch (AccessDeniedException exception) {
                if ("auth.session_expired".equals(exception.code()) || "auth.required".equals(exception.code())) {
                    Platform.runLater(sessionLost);
                } else {
                    Platform.runLater(() -> failed.accept(exception.code()));
                }
            } catch (AppException exception) {
                Platform.runLater(() -> failed.accept(exception.code()));
            } catch (RuntimeException exception) {
                Platform.runLater(() -> failed.accept("error.generic"));
            }
        }, "miyav-console");
        worker.setDaemon(true);
        worker.start();
    }

    /** The member directory, loaded once and shared by all sections. */
    public void members(Consumer<Map<UUID, UserSummary>> ready) {
        if (!members.isEmpty()) {
            ready.accept(members);
            return;
        }
        load(() -> insights.users(session()), list -> ready.accept(remember(list)));
    }

    public Map<UUID, UserSummary> remember(List<UserSummary> list) {
        Map<UUID, UserSummary> byId = new HashMap<>();
        for (UserSummary user : list) {
            byId.put(user.id(), user);
        }
        members = Map.copyOf(byId);
        return members;
    }

    public String memberName(UUID id) {
        UserSummary member = members.get(id);
        return member == null ? "" : member.fullName();
    }
}
