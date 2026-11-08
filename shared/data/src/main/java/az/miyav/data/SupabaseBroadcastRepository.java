package az.miyav.data;

import az.miyav.data.http.HttpExchange;
import az.miyav.domain.admin.Broadcast;
import az.miyav.domain.admin.BroadcastAudience;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.BroadcastRepository;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Admin broadcasts through the admin-only database functions. */
public final class SupabaseBroadcastRepository implements BroadcastRepository {
    private static final Type ROWS = new TypeToken<List<Row>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseBroadcastRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public int audienceSize(AuthSession session, BroadcastAudience audience, String city) {
        JsonObject body = new JsonObject();
        body.addProperty("p_audience", audience.databaseValue());
        city(body, city);
        return JsonParser.parseString(rpc(session, "admin_broadcast_audience", body).body()).getAsInt();
    }

    @Override
    public int send(AuthSession session, String title, String text, BroadcastAudience audience, String city) {
        JsonObject body = new JsonObject();
        body.addProperty("p_title", title);
        body.addProperty("p_body", text);
        body.addProperty("p_audience", audience.databaseValue());
        city(body, city);
        return JsonParser.parseString(rpc(session, "admin_broadcast", body).body()).getAsInt();
    }

    @Override
    public List<Broadcast> recent(AuthSession session) {
        List<Row> rows = client.gson().fromJson(rpc(session, "admin_broadcasts_recent", new JsonObject()).body(), ROWS);
        List<Broadcast> result = new ArrayList<>();
        for (Row row : rows == null ? List.<Row>of() : rows) {
            result.add(new Broadcast(UUID.fromString(row.id), row.title, row.body, BroadcastAudience.fromDatabase(row.audience),
                    row.city, row.recipients, Timestamps.parse(row.created_at), row.actor_name));
        }
        return result;
    }

    private static void city(JsonObject body, String city) {
        if (city == null) {
            body.add("p_city", JsonNull.INSTANCE);
        } else {
            body.addProperty("p_city", city);
        }
    }

    private HttpExchange rpc(AuthSession session, String function, JsonObject body) {
        HttpExchange response = client.send("POST", "/rest/v1/rpc/" + function, session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        return response;
    }

    private static final class Row {
        String id;
        String title;
        String body;
        String audience;
        String city;
        int recipients;
        String created_at;
        String actor_name;
    }
}
