package az.miyav.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class EnvironmentConfig {
    private static final String BUNDLED = "/miyav-client.properties";

    private EnvironmentConfig() {
    }

    public static String url() {
        return value("MIYAV_SUPABASE_URL");
    }

    public static String publishableKey() {
        return value("MIYAV_SUPABASE_PUBLISHABLE_KEY");
    }

    private static String value(String name) {
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        Properties properties = readFile();
        String fromFile = properties.getProperty(name);
        return fromFile == null ? "" : fromFile.trim();
    }

    private static Properties readFile() {
        Properties properties = new Properties();
        Path[] candidates = {
                Path.of("config", "supabase.properties"),
                Path.of("..", "config", "supabase.properties")
        };
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(candidate), StandardCharsets.UTF_8)) {
                properties.load(reader);
                return properties;
            } catch (IOException exception) {
                return properties;
            }
        }
        // Installed builds carry the public project URL and publishable key inside the app.
        try (InputStream bundled = EnvironmentConfig.class.getResourceAsStream(BUNDLED)) {
            if (bundled != null) {
                properties.load(new InputStreamReader(bundled, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Without configuration the app shows its "not configured" message.
        }
        return properties;
    }
}
