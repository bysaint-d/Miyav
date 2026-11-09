package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Live notice that something changed, so a screen can reload right away instead of waiting for its next
 * poll. Callbacks run on a background thread. The session supplier is asked again on every reconnect, so a
 * refreshed access token is picked up. Row-level security decides what each member is told about.
 */
public interface LiveUpdates {
    /** A message was added to this chat. */
    Subscription watchConversation(Supplier<AuthSession> session, UUID conversationId, Runnable onChange);

    /** A notification was added for the signed-in member (new message, offer, application, broadcast). */
    Subscription watchNotifications(Supplier<AuthSession> session, Runnable onChange);

    interface Subscription extends AutoCloseable {
        /** True while joined; callers can poll less often then. */
        boolean live();

        @Override
        void close();
    }

    Subscription NONE = new Subscription() {
        @Override
        public boolean live() {
            return false;
        }

        @Override
        public void close() {
        }
    };

    /** No live connection; screens keep polling. */
    LiveUpdates OFF = new LiveUpdates() {
        @Override
        public Subscription watchConversation(Supplier<AuthSession> session, UUID conversationId, Runnable onChange) {
            return NONE;
        }

        @Override
        public Subscription watchNotifications(Supplier<AuthSession> session, Runnable onChange) {
            return NONE;
        }
    };
}
