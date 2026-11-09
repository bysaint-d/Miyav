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

    /** Uploads a JPEG to the chat's private folder and returns its storage path. */
    String uploadPhoto(AuthSession session, UUID conversationId, byte[] jpeg);

    /** A photo message; the database checks the path belongs to this chat and this sender. */
    Message sendPhoto(AuthSession session, UUID conversationId, String imagePath, String caption, UUID walkListingId);

    byte[] photo(AuthSession session, String imagePath);

    void markRead(AuthSession session, UUID conversationId);

    /**
     * Opens (or reuses) the chat about a walk listing. A walker talks to the owner ({@code otherUserId} is
     * ignored); the owner names the walker who offered.
     */
    UUID openForWalk(AuthSession session, UUID listingId, UUID otherUserId);

    /** Opens (or reuses) the chat with the owner of an active adoption listing. */
    UUID openForAdoption(AuthSession session, UUID listingId);
}
