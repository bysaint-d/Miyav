package az.miyav.android.walk;

import android.content.Context;
import android.content.Intent;
import android.location.Location;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import az.miyav.android.MiyavApplication;
import az.miyav.common.error.AppException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.walk.WalkSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * App-wide owner of the Gəzinti session. The foreground service publishes positions through it; the
 * screen observes {@link #state()}. Nothing is shared with the server unless a session is active.
 */
public final class WalkTracker {
    public static final class State {
        public final WalkSession session;
        public final Double latitude;
        public final Double longitude;
        public final String event;

        State(WalkSession session, Double latitude, Double longitude, String event) {
            this.session = session;
            this.latitude = latitude;
            this.longitude = longitude;
            this.event = event;
        }

        public boolean walking() {
            return session != null && session.status() == WalkSession.Status.ACTIVE;
        }

        public boolean paused() {
            return session != null && session.status() == WalkSession.Status.PAUSED;
        }
    }

    private final MiyavApplication app;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, null, null, null));

    public WalkTracker(MiyavApplication app) {
        this.app = app;
    }

    public LiveData<State> state() {
        return state;
    }

    public State snapshot() {
        State current = state.getValue();
        return current == null ? new State(null, null, null, null) : current;
    }

    /** Reloads the open session from the server, e.g. after the app was restarted. */
    public void sync(Consumer<String> onError) {
        app.io().execute(() -> {
            try {
                AuthSession session = app.auth().requireSession();
                Optional<WalkSession> open = app.walkingService().current(session);
                State previous = snapshot();
                state.postValue(new State(open.orElse(null), previous.latitude, previous.longitude, null));
                if (open.isPresent() && open.get().status() == WalkSession.Status.ACTIVE) {
                    startService();
                } else {
                    stopService();
                }
            } catch (AppException exception) {
                onError.accept(exception.code());
            }
        });
    }

    public void start(PetSpecies species, String petName, String message, Location location, Consumer<String> onError) {
        app.io().execute(() -> {
            try {
                AuthSession session = app.auth().requireSession();
                WalkSession started = app.walkingService().start(session, species, petName, message,
                        location.getLatitude(), location.getLongitude());
                state.postValue(new State(started, location.getLatitude(), location.getLongitude(), "walk.started"));
                startService();
            } catch (AppException exception) {
                onError.accept(exception.code());
            }
        });
    }

    /** Starts a walk with chosen own pets and/or an assigned walk job. */
    public void startWith(List<UUID> petIds, UUID walkListingId, String message, Location location, Consumer<String> onError) {
        app.io().execute(() -> {
            try {
                AuthSession session = app.auth().requireSession();
                WalkSession started = app.walkingService().startWith(session, petIds, walkListingId, message,
                        location.getLatitude(), location.getLongitude());
                state.postValue(new State(started, location.getLatitude(), location.getLongitude(), "walk.started"));
                startService();
            } catch (AppException exception) {
                onError.accept(exception.code());
            }
        });
    }

    public void pause(Consumer<String> onError) {
        app.io().execute(() -> {
            try {
                WalkSession paused = app.walkingService().pause(app.auth().requireSession());
                State previous = snapshot();
                state.postValue(new State(paused, previous.latitude, previous.longitude, null));
                stopService();
            } catch (AppException exception) {
                onError.accept(exception.code());
            }
        });
    }

    public void resume(Location location, Consumer<String> onError) {
        app.io().execute(() -> {
            try {
                AuthSession session = app.auth().requireSession();
                app.walkingService().resume(session);
                WalkSession active = app.walkingService().updateLocation(session, location.getLatitude(), location.getLongitude());
                state.postValue(new State(active, location.getLatitude(), location.getLongitude(), null));
                startService();
            } catch (AppException exception) {
                onError.accept(exception.code());
            }
        });
    }

    public void end(Consumer<String> onError) {
        stopService();
        app.io().execute(() -> {
            try {
                app.walkingService().end(app.auth().requireSession());
                State previous = snapshot();
                state.postValue(new State(null, previous.latitude, previous.longitude, "walk.ended"));
            } catch (AppException exception) {
                if ("walk.no_session".equals(exception.code())) {
                    state.postValue(new State(null, null, null, "walk.ended"));
                } else {
                    onError.accept(exception.code());
                }
            }
        });
    }

    /** Called by the service on the I/O pool. Ends local tracking when the server no longer has a session. */
    void publish(double latitude, double longitude) {
        State previous = snapshot();
        if (!previous.walking()) {
            return;
        }
        try {
            WalkSession updated = app.walkingService().updateLocation(app.auth().requireSession(), latitude, longitude);
            state.postValue(new State(updated, latitude, longitude, null));
        } catch (AppException exception) {
            if ("walk.no_session".equals(exception.code()) || "auth.session_expired".equals(exception.code())) {
                state.postValue(new State(null, latitude, longitude, "walk.no_session"));
                stopService();
            } else {
                state.postValue(new State(previous.session, latitude, longitude, null));
            }
        }
    }

    /** Clears local state on sign-out; the server side expires on its own if the request fails. */
    public void signOut() {
        stopService();
        if (snapshot().session != null) {
            app.io().execute(() -> {
                try {
                    app.walkingService().end(app.auth().requireSession());
                } catch (AppException ignored) {
                    // Without a heartbeat the server ends the session after 10 minutes.
                }
            });
        }
        state.postValue(new State(null, null, null, null));
    }

    private void startService() {
        Context context = app;
        ContextCompat.startForegroundService(context, new Intent(context, WalkLocationService.class));
    }

    private void stopService() {
        app.stopService(new Intent(app, WalkLocationService.class));
    }
}
