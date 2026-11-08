package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.Conversation;
import az.miyav.domain.messaging.Message;

import java.util.List;
import java.util.UUID;

/** Private chats between two signed-in users. Guests have no access at all. */
public interface MessageRepository {
    List<Conversation> inbox(AuthSession session);

    /** The latest {@code limit} messages, oldest first. */
    List<Message> messages(AuthSession session, UUID conversationId, int limit);

    Message send(AuthSession session, UUID conversationId, String body, UUID walkListingId);

    void markRead(AuthSession session, UUID conversationId);

    /**
     * Opens (or reuses) the chat about a walk listing. A walker talks to the owner ({@code otherUserId} is
     * ignored); the owner names the walker who offered.
     */
    UUID openForWalk(AuthSession session, UUID listingId, UUID otherUserId);
}
