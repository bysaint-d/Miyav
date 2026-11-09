package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.PushDeviceRepository;

/** Ties this phone's Firebase token to the signed-in member, and unties it on sign-out. */
public final class PushService {
    private static final String PLATFORM = "fcm";
    private final PushDeviceRepository devices;

    public PushService(PushDeviceRepository devices) {
        this.devices = devices;
    }

    public void register(AuthSession session, String token) {
        requireSession(session);
        devices.register(session, token, PLATFORM);
    }

    public void unregister(AuthSession session, String token) {
        requireSession(session);
        devices.unregister(session, token);
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }
}
