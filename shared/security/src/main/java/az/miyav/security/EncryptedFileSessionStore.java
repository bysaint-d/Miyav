package az.miyav.security;

import az.miyav.domain.auth.AuthSession;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Optional;

public final class EncryptedFileSessionStore implements SessionStore {
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final Path sessionFile;
    private final Path keyFile;

    public EncryptedFileSessionStore(Path directory) {
        this.sessionFile = directory.resolve("session.bin");
        this.keyFile = directory.resolve("session.key");
    }

    @Override
    public synchronized void save(AuthSession session) {
        try {
            Files.createDirectories(sessionFile.getParent());
            SecretKey key = loadOrCreateKey();
            byte[] iv = new byte[IV_LENGTH];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(SessionCodec.encode(session).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            Files.write(sessionFile, payload);
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Unable to persist session", exception);
        }
    }

    @Override
    public synchronized Optional<AuthSession> load() {
        if (!Files.isRegularFile(sessionFile) || !Files.isRegularFile(keyFile)) {
            return Optional.empty();
        }
        try {
            byte[] payload = Files.readAllBytes(sessionFile);
            if (payload.length <= IV_LENGTH) {
                return Optional.empty();
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, readKey(), new GCMParameterSpec(TAG_BITS, iv));
            String json = new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
            return Optional.of(SessionCodec.decode(json));
        } catch (RuntimeException | GeneralSecurityException | IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized void clear() {
        try {
            Files.deleteIfExists(sessionFile);
        } catch (IOException ignored) {
            // Local logout still proceeds if the file cannot be deleted.
        }
    }

    private SecretKey loadOrCreateKey() throws GeneralSecurityException, IOException {
        if (Files.isRegularFile(keyFile)) {
            return readKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        Files.write(keyFile, key.getEncoded());
        return key;
    }

    private SecretKey readKey() throws IOException {
        byte[] encoded = Files.readAllBytes(keyFile);
        return new SecretKeySpec(encoded, "AES");
    }
}
