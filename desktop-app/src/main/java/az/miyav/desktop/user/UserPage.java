package az.miyav.desktop.user;

import javafx.scene.Node;

import java.util.UUID;

/** One page of the member area next to Discover. Built once, refreshed whenever it is opened. */
public abstract class UserPage {
    protected final UserContext context;
    private Node view;

    protected UserPage(UserContext context) {
        this.context = context;
    }

    public abstract String id();

    public abstract String titleKey();

    public abstract String icon();

    protected abstract Node build();

    public abstract void refresh();

    /** Brings one record into view after {@link #refresh()}, e.g. a conversation from a notification. */
    public void focus(UUID id) {
    }

    /** The page is no longer shown; stop live updates and timers. */
    public void hidden() {
    }

    public final Node view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    protected String text(String key, Object... args) {
        return context.text(key, args);
    }
}
