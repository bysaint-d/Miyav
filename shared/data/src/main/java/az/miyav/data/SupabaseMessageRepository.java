package az.miyav.data;

import az.miyav.common.error.PersistenceException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.Conversation;
import az.miyav.domain.messaging.Message;
import az.miyav.domain.repository.MessageRepository;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SupabaseMessageRepository implements MessageRepository {
    private static final Type ROWS = new TypeToken<List<ConversationRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseMessageRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<Conversation> list(AuthSession session) {
        HttpExchange response = client.send(
                "GET",
                "/rest/v1/conversations?select=id,owner_id,sitter_id,last_message_at&order=last_message_at.desc",
                session,
                null
        );
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        List<ConversationRow> rows = client.gson().fromJson(response.body(), ROWS);
        List<Conversation> conversations = new ArrayList<>();
        if (rows == null) {
            return conversations;
        }
        for (ConversationRow row : rows) {
            conversations.add(new Conversation(
                    UUID.fromString(row.id),
                    UUID.fromString(row.owner_id),
                    UUID.fromString(row.sitter_id),
                    Timestamps.parse(row.last_message_at)
            ));
        }
        return conversations;
    }

    @Override
    public List<Message> listMessages(AuthSession session, UUID conversationId) {
        throw NotReady.schema();
    }

    @Override
    public Message send(AuthSession session, UUID conversationId, String body) {
        throw NotReady.schema();
    }

    private static final class ConversationRow {
        String id;
        String owner_id;
        String sitter_id;
        String last_message_at;
    }
}
