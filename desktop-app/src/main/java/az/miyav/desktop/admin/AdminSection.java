package az.miyav.desktop.admin;

import javafx.scene.Node;

import java.util.UUID;

/**
 * One page of the admin console (Overview, Pet Walking, NearMe Map, ...). The shell only knows this
 * type; each subclass builds its own view once and reloads its data on {@link #refresh()}.
 */
public abstract class AdminSection {
    protected final ConsoleContext context;
    private Node view;

    protected AdminSection(ConsoleContext context) {
        this.context = context;
    }

    /** Stable navigation key, also used to open a section from another one. */
    public abstract String id();

    /** Single glyph shown in the sidebar. */
    public abstract String icon();

    public abstract String titleKey();

    public abstract String subtitleKey();

    /** The view is built lazily, once, so switching sections keeps scroll position and filters. */
    public final Node view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    protected abstract Node build();

    /** Loads fresh data from the services. Called when the section is opened and on "Refresh". */
    public abstract void refresh();

    /** Highlights one record, for example a member opened from the map. Sections may ignore it. */
    public void focus(UUID id) {
    }

    protected String text(String key, Object... args) {
        return context.text(key, args);
    }
}
