package az.miyav.data;

import az.miyav.common.error.PersistenceException;
import az.miyav.common.error.ValidationException;
import az.miyav.data.http.HttpBinaryExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.MediaRepository;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class SupabaseMediaRepository implements MediaRepository {
    private static final int ADOPTION_LIMIT = 2 * 1024 * 1024;
    private static final int AVATAR_LIMIT = 5 * 1024 * 1024;
    private static final Pattern OBJECT_PATH = Pattern.compile("^[a-f0-9-]+(/[a-f0-9-]+)+\\.jpg$");
    private static final Pattern AVATAR_PATH = Pattern.compile("^[a-f0-9-]{36}/[A-Za-z0-9._-]{1,120}$");
    private final SupabaseClient client;

    public SupabaseMediaRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public String uploadAdoptionJpeg(AuthSession session, UUID listingId, byte[] jpeg) {
        requireJpeg(jpeg, ADOPTION_LIMIT);
        String path = session.userId() + "/" + listingId + "/" + UUID.randomUUID() + ".jpg";
        upload(session, "adoptions", path, jpeg);
        return path;
    }

    @Override
    public byte[] download(AuthSession session, String path) {
        requirePath(path);
        HttpBinaryExchange response = client.sendBinary(
                "GET", "/storage/v1/object/authenticated/adoptions/" + path, session, null, null, Map.of());
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        return response.body();
    }

    @Override
    public void deleteAdoptionPhoto(AuthSession session, String path) {
        requirePath(path);
        delete(session, "adoptions", path);
    }

    @Override
    public String uploadAvatarJpeg(AuthSession session, byte[] jpeg) {
        requireJpeg(jpeg, AVATAR_LIMIT);
        String path = session.userId() + "/" + UUID.randomUUID() + ".jpg";
        upload(session, "avatars", path, jpeg);
        return path;
    }

    @Override
    public byte[] downloadAvatar(String path) {
        requireAvatarPath(path);
        HttpBinaryExchange response = client.sendBinary(
                "GET", "/storage/v1/object/public/avatars/" + path, null, null, null, Map.of());
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        return response.body();
    }

    @Override
    public void deleteAvatar(AuthSession session, String path) {
        requireAvatarPath(path);
        if (!path.startsWith(session.userId() + "/")) {
            return;
        }
        delete(session, "avatars", path);
    }

    private void upload(AuthSession session, String bucket, String path, byte[] jpeg) {
        HttpBinaryExchange response = client.sendBinary(
                "POST", "/storage/v1/object/" + bucket + "/" + path, session, jpeg, "image/jpeg", Map.of());
        if (response.status() == 413) {
            throw new ValidationException("media.too_large");
        }
        if (response.status() == 401 || response.status() == 403) {
            throw new PersistenceException("access.owner");
        }
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
    }

    private void delete(AuthSession session, String bucket, String path) {
        HttpBinaryExchange response = client.sendBinary(
                "DELETE", "/storage/v1/object/" + bucket + "/" + path, session, null, null, Map.of());
        if (response.status() / 100 != 2 && response.status() != 404) {
            throw new PersistenceException("network.unavailable");
        }
    }

    private static void requireJpeg(byte[] jpeg, int limit) {
        if (jpeg == null || jpeg.length < 3 || jpeg[0] != (byte) 0xFF || jpeg[1] != (byte) 0xD8) {
            throw new ValidationException("validation.pet");
        }
        if (jpeg.length > limit) {
            throw new ValidationException("media.too_large");
        }
    }

    private static void requirePath(String path) {
        if (path == null || !OBJECT_PATH.matcher(path).matches()) {
            throw new ValidationException("validation.pet");
        }
    }

    private static void requireAvatarPath(String path) {
        if (path == null || path.contains("..") || !AVATAR_PATH.matcher(path).matches()) {
            throw new ValidationException("validation.profile");
        }
    }
}
