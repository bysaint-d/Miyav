package az.miyav.data;

import az.miyav.data.http.HttpBinaryExchange;
import az.miyav.data.http.HttpExchange;
import az.miyav.data.http.HttpTransport;
import az.miyav.domain.auth.AuthSession;
import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SupabaseClient {
    private final SupabaseConfig config;
    private final HttpTransport transport;
    private final Gson gson = new Gson();

    public SupabaseClient(SupabaseConfig config, HttpTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    public Gson gson() {
        return gson;
    }

    public SupabaseConfig config() {
        return config;
    }

    /**
     * Opens the pooled connection (DNS, TCP and TLS) ahead of the first real request, so the first screen does
     * not wait for the handshake. Failures are ignored; the next real request simply connects itself.
     */
    public void warmUp() {
        if (!config.configured()) {
            return;
        }
        try {
            send("GET", "/auth/v1/health", null, null);
        } catch (RuntimeException ignored) {
            // Offline or blocked: nothing to warm.
        }
    }

    public HttpExchange send(String method, String pathAndQuery, AuthSession session, String json) {
        return send(method, pathAndQuery, session, json, Map.of());
    }

    public HttpExchange send(
            String method,
            String pathAndQuery,
            AuthSession session,
            String json,
            Map<String, String> extraHeaders
    ) {
        config.requireConfigured();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("apikey", config.publishableKey());
        String bearer = session == null ? config.publishableKey() : session.accessToken();
        headers.put("Authorization", "Bearer " + bearer);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.putAll(extraHeaders);
        return transport.send(method, config.endpoint(pathAndQuery), headers, json);
    }

    public HttpBinaryExchange sendBinary(
            String method,
            String pathAndQuery,
            AuthSession session,
            byte[] body,
            String contentType,
            Map<String, String> extraHeaders
    ) {
        config.requireConfigured();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("apikey", config.publishableKey());
        String bearer = session == null ? config.publishableKey() : session.accessToken();
        headers.put("Authorization", "Bearer " + bearer);
        if (contentType != null && !contentType.isBlank()) {
            headers.put("Content-Type", contentType);
        }
        headers.putAll(extraHeaders);
        return transport.sendBinary(method, config.endpoint(pathAndQuery), headers, body, contentType);
    }
}
