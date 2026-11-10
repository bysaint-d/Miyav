package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.AdminConversation;
import az.miyav.domain.messaging.Message;

import java.util.List;
import java.util.UUID;

/**
 * Moderators reading members' chats. Only administrators pass the server checks, and every opened
 * conversation is written to the audit log.
 */
public interface AdminMessageRepository {
    /** Conversations with at least one message, latest first, matching names or message text. */
    List<AdminConversation> conversations(AuthSession session, String search);

    /** The whole conversation, oldest first. */
    List<Message> messages(AuthSession session, UUID conversationId);

    byte[] photo(AuthSession session, String imagePath);
}
