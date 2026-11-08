package az.miyav.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PkceTest {
    @Test
    void challengeIsTheBase64UrlSha256OfTheVerifier() {
        assertEquals("hUTRhygUtD-A2rxxcJi1zT6li2zeVZidO2r_KMYnjQg",
                Pkce.challenge("dBjftJeZ4CVP-mJ92K3VZrEgQkpnFT4mQ8RmQh4LmQw"));
    }

    @Test
    void verifiersAreLongAndUnique() {
        String first = Pkce.verifier();
        assertTrue(first.length() >= 43 && first.length() <= 128);
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
        assertNotEquals(first, Pkce.verifier());
    }
}
