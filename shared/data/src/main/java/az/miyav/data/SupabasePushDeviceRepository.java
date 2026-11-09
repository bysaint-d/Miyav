package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.PushDeviceRepository;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** push_devices through PostgREST; RLS limits every row to its owner. */
public final class SupabasePushDeviceRepository implements PushDeviceRepository {
    private static final Set<String> PLATFORMS = Set.of("fcm", "expo");
    private final SupabaseClient client;

    public SupabasePushDeviceRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public void register(AuthSession session, String token, String platform) {
        requireSession(session);
        requireToken(token);
        if (!PLATFORMS.contains(platform)) {
            throw new ValidationException("push.platform");
        }
        JsonObject body = new JsonObject();
        body.addProperty("token", token);
        body.addProperty("user_id", session.userId().toString());
        body.addProperty("platform", platform);
        body.addProperty("updated_at", Instant.now().toString());
        HttpExchange response = client.send("POST", "/rest/v1/push_devices?on_conflict=token", session, body.toString(),
                Map.of("Prefer", "resolution=merge-duplicates,return=minimal"));
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    @Override
    public void unregister(AuthSession session, String token) {
        requireSession(session);
        requireToken(token);
        HttpExchange response = client.send("DELETE", "/rest/v1/push_devices?token=eq."
                + URLEncoder.encode(token, StandardCharsets.UTF_8), session, null);
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }

    private static void requireToken(String token) {
        if (token == null || token.length() < 20 || token.length() > 300) {
            throw new ValidationException("push.token");
        }
    }
}
