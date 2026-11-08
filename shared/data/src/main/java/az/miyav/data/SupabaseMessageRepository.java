package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.Conversation;
import az.miyav.domain.messaging.Message;
import az.miyav.domain.repository.MessageRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chats through PostgREST: RLS limits every read and write to the two participants. */
public final class SupabaseMessageRepository implements MessageRepository {
    private static final String SELECT = "id,conversation_id,sender_id,body,image_path,walk_listing_id,price_per_hour,created_at";
    private static final Type INBOX = new TypeToken<List<InboxRow>>() {
    }.getType();
    private static final Type MESSAGES = new TypeToken<List<MessageRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseMessageRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<Conversation> inbox(AuthSession session) {
        requireSession(session);
        HttpExchange response = client.send("POST", "/rest/v1/rpc/my_conversations", session, "{}");
        ensureOk(response);
        List<InboxRow> rows = client.gson().fromJson(response.body(), INBOX);
        List<Conversation> result = new ArrayList<>();
        for (InboxRow row : rows == null ? List.<InboxRow>of() : rows) {
            UUID sender = row.last_sender_id == null ? null : UUID.fromString(row.last_sender_id);
            result.add(new Conversation(UUID.fromString(row.id), UUID.fromString(row.other_id), row.other_name,
                    Timestamps.parse(row.last_message_at), row.last_body, session.userId().equals(sender),
                    row.last_price, row.unread));
        }
        return result;
    }

    @Override
    public List<Message> messages(AuthSession session, UUID conversationId, int limit) {
        requireSession(session);
        HttpExchange response = client.send("GET", "/rest/v1/messages?select=" + SELECT + "&conversation_id=eq."
                + conversationId + "&order=created_at.desc&limit=" + limit, session, null);
        List<Message> result = rows(response);
        Collections.reverse(result);
        return result;
    }

    @Override
    public Message send(AuthSession session, UUID conversationId, String body, UUID walkListingId) {
        requireSession(session);
        JsonObject json = new JsonObject();
        json.addProperty("conversation_id", conversationId.toString());
        json.addProperty("sender_id", session.userId().toString());
        json.addProperty("body", body);
        if (walkListingId != null) {
            json.addProperty("walk_listing_id", walkListingId.toString());
        }
        HttpExchange response = client.send("POST", "/rest/v1/messages?select=" + SELECT, session, json.toString(),
                Map.of("Prefer", "return=representation"));
        List<Message> saved = rows(response);
        if (saved.isEmpty()) {
            throw new PersistenceException("network.unavailable");
        }
        return saved.get(0);
    }

    @Override
    public void markRead(AuthSession session, UUID conversationId) {
        requireSession(session);
        JsonObject json = new JsonObject();
        json.addProperty("p_conversation_id", conversationId.toString());
        ensureOk(client.send("POST", "/rest/v1/rpc/read_conversation", session, json.toString()));
    }

    @Override
    public UUID openForWalk(AuthSession session, UUID listingId, UUID otherUserId) {
        requireSession(session);
        JsonObject json = new JsonObject();
        json.addProperty("p_listing", listingId.toString());
        if (otherUserId != null) {
            json.addProperty("p_with", otherUserId.toString());
        }
        HttpExchange response = client.send("POST", "/rest/v1/rpc/open_walk_conversation", session, json.toString());
        ensureOk(response);
        return UUID.fromString(JsonParser.parseString(response.body()).getAsString());
    }

    private List<Message> rows(HttpExchange response) {
        ensureOk(response);
        List<MessageRow> rows = client.gson().fromJson(response.body(), MESSAGES);
        List<Message> result = new ArrayList<>();
        for (MessageRow row : rows == null ? List.<MessageRow>of() : rows) {
            result.add(new Message(UUID.fromString(row.id), UUID.fromString(row.conversation_id),
                    UUID.fromString(row.sender_id), row.body,
                    row.walk_listing_id == null ? null : UUID.fromString(row.walk_listing_id),
                    row.price_per_hour, row.image_path != null, Timestamps.parse(row.created_at)));
        }
        return result;
    }

    private static void ensureOk(HttpExchange response) {
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }

    private static final class InboxRow {
        String id;
        String other_id;
        String other_name;
        String last_message_at;
        String last_body;
        String last_sender_id;
        BigDecimal last_price;
        boolean unread;
    }

    private static final class MessageRow {
        String id;
        String conversation_id;
        String sender_id;
        String body;
        String image_path;
        String walk_listing_id;
        BigDecimal price_per_hour;
        String created_at;
    }
}
