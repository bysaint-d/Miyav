package az.miyav.android.push;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import az.miyav.android.BuildConfig;
import az.miyav.android.MiyavApplication;
import az.miyav.common.error.AppException;
import az.miyav.domain.auth.AuthSession;

/**
 * Firebase Cloud Messaging for MIYAV. Starts only when the build carries Firebase settings; otherwise every
 * call is a no-op and the background check delivers notifications instead.
 */
public final class Push {
    private static final String PREFS = "miyav_push";
    private static final String TOKEN = "fcm_token";

    private Push() {
    }

    public static boolean configured() {
        return !BuildConfig.FIREBASE_PROJECT_ID.isEmpty() && !BuildConfig.FIREBASE_APP_ID.isEmpty()
                && !BuildConfig.FIREBASE_API_KEY.isEmpty() && !BuildConfig.FIREBASE_SENDER_ID.isEmpty();
    }

    public static void init(Context context) {
        if (!configured() || !FirebaseApp.getApps(context).isEmpty()) {
            return;
        }
        FirebaseApp.initializeApp(context, new FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build());
    }

    /** After sign-in or app start: ties this phone's token to the member. */
    public static void register(MiyavApplication app) {
        if (!configured() || app.auth().viewer() == null) {
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> tokenChanged(app, token));
    }

    static void tokenChanged(MiyavApplication app, String token) {
        if (!configured() || token == null || token.isEmpty()) {
            return;
        }
        prefs(app).edit().putString(TOKEN, token).apply();
        AuthSession session = app.auth().viewer();
        if (session == null) {
            return;
        }
        app.io().execute(() -> {
            try {
                app.push().register(session, token);
            } catch (AppException ignored) {
                // Retried on the next app start; the background check still delivers meanwhile.
            }
        });
    }

    /**
     * Before the session is dropped: unties the token so the next account on this phone does not receive
     * this member's pushes. Runs on the calling thread.
     */
    public static void unregister(MiyavApplication app, AuthSession session) {
        String token = prefs(app).getString(TOKEN, "");
        if (!configured() || session == null || token.isEmpty()) {
            return;
        }
        try {
            app.push().unregister(session, token);
        } catch (AppException ignored) {
            // The server drops tokens that stop working; a stale row only costs one failed send.
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
