package az.miyav.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignInMethodsTest {
    private static String token(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    @Test
    void googleOnlyAccountsHaveNoPassword() {
        String google = token("{\"sub\":\"1\",\"app_metadata\":{\"provider\":\"google\",\"providers\":[\"google\"]}}");
        assertEquals(Set.of("google"), SignInMethods.providers(google));
        assertFalse(SignInMethods.hasPassword(google));
    }

    @Test
    void emailAccountsAndLinkedAccountsHaveAPassword() {
        assertTrue(SignInMethods.hasPassword(token("{\"app_metadata\":{\"provider\":\"email\",\"providers\":[\"email\"]}}")));
        assertTrue(SignInMethods.hasPassword(token("{\"app_metadata\":{\"provider\":\"google\",\"providers\":[\"google\", \"email\"]}}")));
    }

    @Test
    void unreadableTokensKeepThePasswordOption() {
        assertTrue(SignInMethods.hasPassword("not-a-jwt"));
        assertTrue(SignInMethods.hasPassword(null));
    }
}
