package az.miyav.application;

import az.miyav.common.error.PersistenceException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.MediaRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.user.User;

import java.util.Optional;
import java.util.UUID;
import az.miyav.domain.validation.ProfileValidator;

public final class UserService {
    private final UserRepository users;
    private final ProfileValidator profiles;
    private final MediaRepository media;

    public UserService(UserRepository users, ProfileValidator profiles) {
        this(users, profiles, null);
    }

    public UserService(UserRepository users, ProfileValidator profiles, MediaRepository media) {
        this.users = users;
        this.profiles = profiles;
        this.media = media;
    }

    public User current(AuthSession session) {
        return users.findById(session).orElseThrow(() -> new PersistenceException("auth.profile_unavailable"));
    }

    public User updateProfile(AuthSession session, String fullName, String city, String district, String phone) {
        User current = current(session);
        User updated = current.withProfile(
                fullName == null ? "" : fullName.trim(),
                city,
                district == null ? "" : district.trim(),
                phone == null ? "" : phone.trim()
        );
        profiles.validate(updated);
        return users.updateProfile(session, updated);
    }

    public User updateProfile(AuthSession session, String fullName, String city, String district, String phone,
                              String bio, boolean phonePublic) {
        User current = current(session);
        User updated = current.withProfile(
                fullName == null ? "" : fullName.trim(),
                city,
                district == null ? "" : district.trim(),
                phone == null ? "" : phone.trim(),
                bio == null ? "" : bio.trim(),
                phonePublic
        );
        profiles.validate(updated);
        return users.updateProfile(session, updated);
    }

    /** Public card of a publisher or walker; empty when the profile is not visible to the caller. */
    public Optional<PublicProfile> publicProfile(AuthSession session, UUID userId) {
        return users.publicProfile(session, userId);
    }

    public byte[] avatar(PublicProfile profile) {
        requireMedia();
        if (profile.avatarPath() == null) {
            return new byte[0];
        }
        return media.downloadAvatar(profile.avatarPath());
    }

    public byte[] avatar(String avatarPath) {
        requireMedia();
        if (avatarPath == null) {
            return new byte[0];
        }
        return media.downloadAvatar(avatarPath);
    }

    public User updateLocale(AuthSession session, LocaleCode locale) {
        User current = current(session);
        return users.updateProfile(session, current.withLocale(locale));
    }

    public User updateAvatar(AuthSession session, byte[] jpeg) {
        requireMedia();
        User current = current(session);
        String path = media.uploadAvatarJpeg(session, jpeg);
        User updated = users.updateProfile(session, current.withAvatar(path));
        if (current.avatarPath() != null) {
            try {
                media.deleteAvatar(session, current.avatarPath());
            } catch (RuntimeException ignored) {
                // The profile already points at the new image; an orphaned old file is harmless.
            }
        }
        return updated;
    }

    public byte[] avatar(User user) {
        requireMedia();
        if (user.avatarPath() == null) {
            return new byte[0];
        }
        return media.downloadAvatar(user.avatarPath());
    }

    private void requireMedia() {
        if (media == null) {
            throw new PersistenceException("schema.not_ready");
        }
    }
}
