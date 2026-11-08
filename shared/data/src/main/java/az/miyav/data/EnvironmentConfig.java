package az.miyav.data;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class EnvironmentConfig {
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
        return properties;
    }
}
