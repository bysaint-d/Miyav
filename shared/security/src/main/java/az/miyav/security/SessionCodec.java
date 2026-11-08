package az.miyav.security;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.user.UserRole;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SessionCodec {
    private SessionCodec() {
    }

    public static String encode(AuthSession session) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("userId", session.userId().toString());
        fields.put("email", session.email());
        fields.put("accessToken", session.accessToken());
        fields.put("refreshToken", session.refreshToken());
        fields.put("expiresAt", session.expiresAt().toString());
        fields.put("role", session.role() == null ? "" : session.role().name());
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(field.getKey()).append("\":\"").append(escape(field.getValue())).append('"');
        }
        return json.append('}').toString();
    }

    public static AuthSession decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty session");
        }
        Map<String, String> fields = parseObject(json);
        String roleValue = fields.getOrDefault("role", "");
        UserRole role = roleValue.isBlank() ? null : UserRole.valueOf(roleValue);
        return new AuthSession(
                UUID.fromString(required(fields, "userId")),
                fields.getOrDefault("email", ""),
                required(fields, "accessToken"),
                fields.getOrDefault("refreshToken", ""),
                Instant.parse(required(fields, "expiresAt")),
                role
        );
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key);
        }
        return value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Map<String, String> parseObject(String json) {
        String body = json.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new IllegalArgumentException("session json");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        String inner = body.substring(1, body.length() - 1);
        int index = 0;
        while (index < inner.length()) {
            int keyStart = inner.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = inner.indexOf('"', keyStart + 1);
            String key = inner.substring(keyStart + 1, keyEnd);
            int valueStart = inner.indexOf('"', keyEnd + 1);
            StringBuilder value = new StringBuilder();
            int cursor = valueStart + 1;
            while (cursor < inner.length()) {
                char character = inner.charAt(cursor);
                if (character == '\\' && cursor + 1 < inner.length()) {
                    value.append(inner.charAt(cursor + 1));
                    cursor += 2;
                    continue;
                }
                if (character == '"') {
                    break;
                }
                value.append(character);
                cursor++;
            }
            fields.put(key, value.toString());
            index = cursor + 1;
        }
        return fields;
    }
}
