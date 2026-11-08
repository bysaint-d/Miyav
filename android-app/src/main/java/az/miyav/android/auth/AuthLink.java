package az.miyav.android.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses what Supabase Auth appends to a redirect: tokens for email confirmation and recovery, or a
 * one-time PKCE {@code code} after Google sign-in.
 */
public final class AuthLink {
    public enum Kind { SIGNUP, RECOVERY, CODE, ERROR, UNKNOWN }

    private final Map<String, String> values;

    private AuthLink(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static AuthLink parse(String fragment, String query) {
        Map<String, String> values = new LinkedHashMap<>();
        read(query, values);
        read(fragment, values);
        return new AuthLink(values);
    }

    public Kind kind() {
        if (values.containsKey("error") || values.containsKey("error_code")) {
            return Kind.ERROR;
        }
        if (accessToken().isEmpty()) {
            return code().isEmpty() ? Kind.UNKNOWN : Kind.CODE;
        }
        return "recovery".equals(values.get("type")) ? Kind.RECOVERY : Kind.SIGNUP;
    }

    public String code() {
        return values.getOrDefault("code", "");
    }

    public String accessToken() {
        return values.getOrDefault("access_token", "");
    }

    public String refreshToken() {
        return values.getOrDefault("refresh_token", "");
    }

    public long expiresIn() {
        try {
            return Long.parseLong(values.getOrDefault("expires_in", "0"));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    public String errorCode() {
        return values.getOrDefault("error_code", values.getOrDefault("error", ""));
    }

    private static void read(String encoded, Map<String, String> into) {
        if (encoded == null || encoded.isBlank()) {
            return;
        }
        for (String pair : encoded.split("&")) {
            int split = pair.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, split));
            String value = decode(pair.substring(split + 1));
            into.put(key, value);
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception exception) {
            return "";
        }
    }
}
