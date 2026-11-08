package az.miyav.domain.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * The protected action a guest tried before signing in, with the arguments needed to return to it
 * (for example the listing id of an adoption application). Arguments are plain strings so the action
 * survives process death on Android and app restarts on desktop.
 */
public final class PendingAction {
    private final ProtectedAction action;
    private final Map<String, String> arguments;

    private PendingAction(ProtectedAction action, Map<String, String> arguments) {
        this.action = Objects.requireNonNull(action, "action");
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    public static PendingAction of(ProtectedAction action) {
        return new PendingAction(action, Map.of());
    }

    public static PendingAction of(ProtectedAction action, String key, String value) {
        Map<String, String> arguments = new LinkedHashMap<>();
        if (key != null && value != null) {
            arguments.put(key, value);
        }
        return new PendingAction(action, arguments);
    }

    public PendingAction with(String key, String value) {
        Map<String, String> arguments = new LinkedHashMap<>(this.arguments);
        if (key != null && value != null) {
            arguments.put(key, value);
        }
        return new PendingAction(action, arguments);
    }

    public ProtectedAction action() {
        return action;
    }

    public String argument(String key) {
        return arguments.getOrDefault(key, "");
    }

    public Map<String, String> arguments() {
        return arguments;
    }

    /** True when this is the given action for the given argument value, e.g. APPLY_ADOPTION for one pet. */
    public boolean matches(ProtectedAction expected, String key, String value) {
        return action == expected && (key == null || Objects.equals(arguments.get(key), value));
    }

    /** Compact single-line form for preference storage: {@code ACTION|key=value|key=value}. */
    public String encode() {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(action.name());
        arguments.forEach((key, value) -> joiner.add(escape(key) + "=" + escape(value)));
        return joiner.toString();
    }

    public static PendingAction decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        String[] parts = encoded.split("\\|");
        ProtectedAction action = ProtectedAction.fromName(parts[0]);
        if (action == null) {
            return null;
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            int split = parts[i].indexOf('=');
            if (split > 0) {
                arguments.put(unescape(parts[i].substring(0, split)), unescape(parts[i].substring(split + 1)));
            }
        }
        return new PendingAction(action, arguments);
    }

    private static String escape(String value) {
        return value.replace("%", "%25").replace("|", "%7C").replace("=", "%3D");
    }

    private static String unescape(String value) {
        return value.replace("%3D", "=").replace("%7C", "|").replace("%25", "%");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PendingAction that && action == that.action && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, arguments);
    }

    @Override
    public String toString() {
        return "PendingAction{" + action + ", " + arguments.keySet() + "}";
    }
}
