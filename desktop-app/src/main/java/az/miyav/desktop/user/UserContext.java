package az.miyav.desktop.user;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.AppException;
import az.miyav.desktop.DesktopGraph;
import az.miyav.domain.auth.AuthSession;
import javafx.application.Platform;
import javafx.stage.Window;

import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** What every member page needs: services, translations, background work, navigation and the status line. */
public final class UserContext {
    private final DesktopGraph graph;
    private final Consumer<String> status;
    private final BiConsumer<String, UUID> navigator;
    private final Runnable sessionLost;
    private final Runnable badgesChanged;
    private final Supplier<Window> window;

    public UserContext(DesktopGraph graph, Consumer<String> status, BiConsumer<String, UUID> navigator,
                       Runnable sessionLost, Runnable badgesChanged, Supplier<Window> window) {
        this.graph = graph;
        this.status = status;
        this.navigator = navigator;
        this.sessionLost = sessionLost;
        this.badgesChanged = badgesChanged;
        this.window = window;
    }

    public DesktopGraph graph() {
        return graph;
    }

    public AuthSession session() {
        return graph.session().requireSession();
    }

    public String text(String key, Object... args) {
        return graph.text(key, args);
    }

    public Locale locale() {
        return Locale.forLanguageTag(graph.catalog().locale().tag());
    }

    public Window window() {
        return window.get();
    }

    /** Switches to another page, optionally focused on one record (a conversation, a listing). */
    public void open(String pageId, UUID focus) {
        navigator.accept(pageId, focus);
    }

    public void status(String message) {
        status.accept(message);
    }

    /** Unread counts on the navigation may have changed. */
    public void badgesChanged() {
        badgesChanged.run();
    }

    public <T> void load(Supplier<T> work, Consumer<T> ok) {
        load(work, ok, code -> status(text(code)));
    }

    /** Runs {@code work} off the UI thread and hands the result back on it. An expired session signs out. */
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
        }, "miyav-member");
        worker.setDaemon(true);
        worker.start();
    }
}
