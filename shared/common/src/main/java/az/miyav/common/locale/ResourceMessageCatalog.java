package az.miyav.common.locale;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class ResourceMessageCatalog implements MessageCatalog {
    private final LocaleCode locale;
    private final Map<String, String> messages;

    public ResourceMessageCatalog(LocaleCode locale) {
        this.locale = locale == null ? LocaleCode.AZ : locale;
        this.messages = load(this.locale);
    }

    @Override
    public LocaleCode locale() {
        return locale;
    }

    @Override
    public String text(String code) {
        return messages.getOrDefault(code, code);
    }

    public Map<String, String> messages() {
        return messages;
    }

    private static Map<String, String> load(LocaleCode locale) {
        String path = "/az/miyav/common/locale/messages_" + locale.tag() + ".properties";
        try (InputStream input = ResourceMessageCatalog.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing catalog " + path);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            Map<String, String> values = new HashMap<>();
            for (String name : properties.stringPropertyNames()) {
                values.put(name, properties.getProperty(name));
            }
            return Collections.unmodifiableMap(values);
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable catalog " + path, exception);
        }
    }
}
