package az.miyav.android.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import az.miyav.domain.auth.AuthSession;
import az.miyav.security.EncryptedFileSessionStore;
import az.miyav.security.SessionCodec;
import az.miyav.security.SessionStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the session encrypted with an AES-256-GCM key held by Android Keystore. The key never leaves
 * the keystore, so copying the app's files to another device does not reveal the tokens.
 */
public final class AndroidSessionStore implements SessionStore {
    private static final String KEY_ALIAS = "miyav_session_v1";
    private static final String PREFERENCES = "miyav_secure_session";
    private static final String ENTRY = "session";
    private static final int IV_LENGTH = 12;
    private final SharedPreferences preferences;
    private final Path legacyDirectory;
    /** Decrypted copy kept in memory; Keystore is only used when the session is written or first read. */
    private Optional<AuthSession> cached;

    public AndroidSessionStore(Context context) {
        Context app = context.getApplicationContext();
        this.preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        this.legacyDirectory = app.getFilesDir().toPath().resolve("session");
    }

    @Override
    public synchronized void save(AuthSession session) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(SessionCodec.encode(session).getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            preferences.edit().putString(ENTRY, Base64.encodeToString(payload, Base64.NO_WRAP)).commit();
            cached = Optional.of(session);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect the session", exception);
        }
    }

    @Override
    public synchronized Optional<AuthSession> load() {
        if (cached == null) {
            cached = decrypt();
        }
        return cached;
    }

    private Optional<AuthSession> decrypt() {
        String stored = preferences.getString(ENTRY, null);
        if (stored == null) {
            return migrateLegacy();
        }
        try {
            byte[] payload = Base64.decode(stored, Base64.NO_WRAP);
            if (payload.length <= IV_LENGTH) {
                clear();
                return Optional.empty();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return Optional.of(SessionCodec.decode(new String(plain, StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            clear();
            return Optional.empty();
        }
    }

    @Override
    public synchronized void clear() {
        cached = Optional.empty();
        preferences.edit().remove(ENTRY).commit();
        new EncryptedFileSessionStore(legacyDirectory).clear();
    }

    private Optional<AuthSession> migrateLegacy() {
        EncryptedFileSessionStore legacy = new EncryptedFileSessionStore(legacyDirectory);
        Optional<AuthSession> session = legacy.load();
        legacy.clear();
        try {
            java.nio.file.Files.deleteIfExists(legacyDirectory.resolve("session.key"));
        } catch (Exception ignored) {
            // A leftover key file without its session is inert.
        }
        session.ifPresent(this::save);
        return session;
    }

    private static SecretKey key() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }
}
