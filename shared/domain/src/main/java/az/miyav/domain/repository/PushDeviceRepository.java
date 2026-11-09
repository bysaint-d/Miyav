package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;

/** Phone push tokens of the signed-in member; the server sends every new notification to them. */
public interface PushDeviceRepository {
    /** Saves or refreshes the token for this member. {@code platform} is "fcm" for the native app. */
    void register(AuthSession session, String token, String platform);

    /** Removes the token, e.g. on sign-out, so the next account on this phone does not get these pushes. */
    void unregister(AuthSession session, String token);
}
