package az.miyav.security;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedFileSessionStoreTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsASessionWithoutWritingTheTokenAsPlainText() throws Exception {
        EncryptedFileSessionStore store = new EncryptedFileSessionStore(directory);
        UUID userId = UUID.randomUUID();
        AuthSession session = new AuthSession(userId, "a@example.com", "secret-token", "refresh", Instant.parse("2026-11-08T01:00:00Z"), UserRole.OWNER);
        store.save(session);
        AuthSession loaded = store.load().orElseThrow();
        assertEquals(userId, loaded.userId());
        assertEquals("secret-token", loaded.accessToken());
        byte[] onDisk = java.nio.file.Files.readAllBytes(directory.resolve("session.bin"));
        assertFalse(new String(onDisk, java.nio.charset.StandardCharsets.ISO_8859_1).contains("secret-token"));
        store.clear();
        assertTrue(store.load().isEmpty());
    }
}
