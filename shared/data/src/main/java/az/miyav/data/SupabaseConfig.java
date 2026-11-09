package az.miyav.data;

import az.miyav.common.error.ConfigurationException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class SupabaseConfig {
    private final String url;
    private final String publishableKey;

    private SupabaseConfig(String url, String publishableKey) {
        this.url = url;
        this.publishableKey = publishableKey;
    }

    public static SupabaseConfig parse(String url, String publishableKey) {
        String normalizedUrl = url == null ? "" : url.trim();
        String key = publishableKey == null ? "" : publishableKey.trim();
        if (normalizedUrl.isEmpty() || key.isEmpty()) {
            return new SupabaseConfig("", "");
        }
        validateUrl(normalizedUrl);
        rejectSecret(key);
        return new SupabaseConfig(stripTrailingSlash(normalizedUrl), key);
    }

    public boolean configured() {
        return !url.isEmpty() && !publishableKey.isEmpty();
    }

    public void requireConfigured() {
        if (!configured()) {
            throw new ConfigurationException("config.missing");
        }
    }

    public URI endpoint(String pathAndQuery) {
        requireConfigured();
        return URI.create(url + pathAndQuery);
    }

    /** Realtime websocket endpoint; the publishable key identifies the project, the user token joins channels. */
    public String realtimeUrl() {
        requireConfigured();
        return url.replaceFirst("^http", "ws") + "/realtime/v1/websocket?apikey="
                + java.net.URLEncoder.encode(publishableKey, StandardCharsets.UTF_8) + "&vsn=1.0.0";
    }

    public String publishableKey() {
        requireConfigured();
        return publishableKey;
    }

    @Override
    public String toString() {
        return "SupabaseConfig{configured=" + configured() + "}";
    }

    private static void validateUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException("config.rejected");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("10.0.2.2");
        if ("https".equals(scheme) || ("http".equals(scheme) && local)) {
            return;
        }
        throw new ConfigurationException("config.rejected");
    }

    private static void rejectSecret(String key) {
        if (key.startsWith("sb_secret_")) {
            throw new ConfigurationException("config.rejected");
        }
        String[] parts = key.split("\\.");
        if (parts.length != 3) {
            return;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonObject payload = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            if (payload.has("role") && "service_role".equals(payload.get("role").getAsString())) {
                throw new ConfigurationException("config.rejected");
            }
        } catch (ConfigurationException exception) {
            throw exception;
        } catch (RuntimeException ignored) {
            throw new ConfigurationException("config.rejected");
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
