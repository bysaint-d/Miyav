package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.SafetyRepository;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** {@code report_user} RPC and the {@code user_blocks} table; RLS keeps every block private to its owner. */
public final class SupabaseSafetyRepository implements SafetyRepository {
    private static final Type ROWS = new TypeToken<List<Row>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseSafetyRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public void report(AuthSession session, UUID reportedId, String reason) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("p_reported_id", reportedId.toString());
        body.addProperty("p_reason", reason);
        ensureOk(client.send("POST", "/rest/v1/rpc/report_user", session, body.toString()));
    }

    @Override
    public Set<UUID> blocked(AuthSession session) {
        requireSession(session);
        HttpExchange response = client.send("GET", "/rest/v1/user_blocks?select=blocked_id", session, null);
        ensureOk(response);
        List<Row> rows = client.gson().fromJson(response.body(), ROWS);
        Set<UUID> ids = new LinkedHashSet<>();
        if (rows != null) {
            for (Row row : rows) {
                ids.add(UUID.fromString(row.blocked_id));
            }
        }
        return ids;
    }

    @Override
    public void block(AuthSession session, UUID userId) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("blocked_id", userId.toString());
        HttpExchange response = client.send("POST", "/rest/v1/user_blocks", session, body.toString(),
                Map.of("Prefer", "resolution=ignore-duplicates,return=minimal"));
        if (response.status() != 409) {
            ensureOk(response);
        }
    }

    @Override
    public void unblock(AuthSession session, UUID userId) {
        requireSession(session);
        ensureOk(client.send("DELETE", "/rest/v1/user_blocks?blocked_id=eq." + userId, session, null));
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

    private static final class Row {
        String blocked_id;
    }
}
