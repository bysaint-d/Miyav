package az.miyav.common.locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceMessageCatalogTest {
    @Test
    void catalogsShareKeysAndKeepAzerbaijaniCharacters() {
        ResourceMessageCatalog az = new ResourceMessageCatalog(LocaleCode.AZ);
        ResourceMessageCatalog en = new ResourceMessageCatalog(LocaleCode.EN);
        ResourceMessageCatalog ru = new ResourceMessageCatalog(LocaleCode.RU);
        assertEquals(az.messages().keySet(), en.messages().keySet());
        assertEquals(az.messages().keySet(), ru.messages().keySet());
        assertTrue(az.text("auth.password").contains("Şifrə"));
        assertEquals("MIYAV", en.text("app.name"));
    }
}
