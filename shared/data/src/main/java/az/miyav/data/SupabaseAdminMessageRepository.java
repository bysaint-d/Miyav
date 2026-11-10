package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.common.error.ValidationException;
import az.miyav.data.http.HttpBinaryExchange;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.AdminConversation;
import az.miyav.domain.messaging.Message;
import az.miyav.domain.repository.AdminMessageRepository;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** The {@code admin_conversations} and {@code admin_conversation_messages} RPCs; the server audits each read. */
public final class SupabaseAdminMessageRepository implements AdminMessageRepository {
    private static final Pattern PHOTO_PATH = Pattern.compile("^[a-f0-9-]{36}/[a-f0-9-]{36}/[A-Za-z0-9._-]{1,120}$");
    private static final Type ROWS = new TypeToken<List<ConversationRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseAdminMessageRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<AdminConversation> conversations(AuthSession session, String search) {
        JsonObject body = new JsonObject();
        body.addProperty("p_search", search == null ? "" : search.trim());
        List<ConversationRow> rows = client.gson().fromJson(call(session, "admin_conversations", body), ROWS);
        List<AdminConversation> result = new ArrayList<>();
        for (ConversationRow row : rows == null ? List.<ConversationRow>of() : rows) {
            result.add(new AdminConversation(UUID.fromString(row.id), UUID.fromString(row.owner_id), row.owner_name,
                    UUID.fromString(row.sitter_id), row.sitter_name,
                    row.last_message_at == null ? null : Timestamps.parse(row.last_message_at),
                    row.last_body, row.last_has_image, row.message_count));
        }
        return result;
    }

    @Override
    public List<Message> messages(AuthSession session, UUID conversationId) {
        JsonObject body = new JsonObject();
        body.addProperty("p_conversation", conversationId.toString());
        return SupabaseMessageRepository.parse(client, call(session, "admin_conversation_messages", body));
    }

    @Override
    public byte[] photo(AuthSession session, String imagePath) {
        if (imagePath == null || !PHOTO_PATH.matcher(imagePath).matches()) {
            throw new ValidationException("chat.photo");
        }
        HttpBinaryExchange response = client.sendBinary("GET", "/storage/v1/object/authenticated/messages/" + imagePath,
                requireSession(session), null, null, Map.of());
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        return response.body();
    }

    private String call(AuthSession session, String function, JsonObject body) {
        HttpExchange response = client.send("POST", "/rest/v1/rpc/" + function, requireSession(session), body.toString());
        if (response.status() / 100 != 2) {
            RuntimeException mapped = PostgrestErrors.from(response);
            if (mapped instanceof AccessDeniedException) {
                throw new AccessDeniedException("access.admin");
            }
            throw mapped;
        }
        return response.body();
    }

    private static AuthSession requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("access.admin");
        }
        return session;
    }

    private static final class ConversationRow {
        String id;
        String owner_id;
        String owner_name;
        String sitter_id;
        String sitter_name;
        String last_message_at;
        String last_body;
        boolean last_has_image;
        int message_count;
    }
}
