package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;

import java.util.UUID;

public interface MediaRepository {
    String uploadAdoptionJpeg(AuthSession session, UUID listingId, byte[] jpeg);

    byte[] download(AuthSession session, String path);

    default void deleteAdoptionPhoto(AuthSession session, String path) {
        throw new UnsupportedOperationException("deleteAdoptionPhoto");
    }

    default String uploadAvatarJpeg(AuthSession session, byte[] jpeg) {
        throw new UnsupportedOperationException("uploadAvatarJpeg");
    }

    default byte[] downloadAvatar(String path) {
        throw new UnsupportedOperationException("downloadAvatar");
    }

    default void deleteAvatar(AuthSession session, String path) {
        throw new UnsupportedOperationException("deleteAvatar");
    }
}
