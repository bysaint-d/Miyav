package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.Conversation;
import az.miyav.domain.messaging.Message;

import java.util.List;
import java.util.UUID;

public interface MessageRepository {
    List<Conversation> list(AuthSession session);

    List<Message> listMessages(AuthSession session, UUID conversationId);

    Message send(AuthSession session, UUID conversationId, String body);
}
