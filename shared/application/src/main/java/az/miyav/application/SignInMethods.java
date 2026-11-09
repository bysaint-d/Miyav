package az.miyav.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads how the user signed up ({@code app_metadata.providers}) from a Supabase access token. */
final class SignInMethods {
    private static final Pattern PROVIDERS = Pattern.compile("\"providers\"\\s*:\\s*\\[([^\\]]*)]");
    private static final Pattern PROVIDER = Pattern.compile("\"provider\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME = Pattern.compile("\"([^\"]+)\"");

    private SignInMethods() {
    }

    /** Providers in the token, such as {@code email} or {@code google}; empty when the token cannot be read. */
    static Set<String> providers(String accessToken) {
        Set<String> found = new LinkedHashSet<>();
        String[] parts = accessToken == null ? new String[0] : accessToken.split("\\.");
        if (parts.length < 2) {
            return found;
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return found;
        }
        Matcher list = PROVIDERS.matcher(payload);
        if (list.find()) {
            Matcher name = NAME.matcher(list.group(1));
            while (name.find()) {
                found.add(name.group(1));
            }
        }
        Matcher single = PROVIDER.matcher(payload);
        if (found.isEmpty() && single.find()) {
            found.add(single.group(1));
        }
        return found;
    }

    /** True unless the token proves the account has no email/password identity. */
    static boolean hasPassword(String accessToken) {
        Set<String> providers = providers(accessToken);
        return providers.isEmpty() || providers.contains("email");
    }
}
