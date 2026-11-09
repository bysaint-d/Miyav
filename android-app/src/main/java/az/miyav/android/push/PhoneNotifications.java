package az.miyav.android.push;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import az.miyav.android.MainActivity;
import az.miyav.android.R;
import az.miyav.android.sync.NotificationPollWorker;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Posts MIYAV notifications on the phone. Push (FCM) and the background check both come through here, and
 * each notification id is shown at most once whichever path delivers it first.
 */
public final class PhoneNotifications {
    private static final String CHANNEL = "miyav_updates";
    private static final String PREFS = "miyav_push";
    private static final String LAST_SHOWN = "last_shown_ms";
    private static final String SHOWN_IDS = "shown_ids";
    private static final int REMEMBERED = 100;

    private PhoneNotifications() {
    }

    public static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Newest notification time already shown; the background check only looks at newer ones. */
    public static long lastShown(Context context, long fallback) {
        return prefs(context).getLong(LAST_SHOWN, fallback);
    }

    public static void markChecked(Context context, long millis) {
        SharedPreferences prefs = prefs(context);
        if (millis > prefs.getLong(LAST_SHOWN, 0)) {
            prefs.edit().putLong(LAST_SHOWN, millis).apply();
        }
    }

    /** Shows one notification unless it was shown before. Returns false when skipped. */
    @SuppressWarnings("MissingPermission")
    public static synchronized boolean show(Context context, String id, String title, String body,
                                            String conversationId, long createdAtMillis) {
        if (!allowed(context) || id == null || id.isEmpty()) {
            return false;
        }
        SharedPreferences prefs = prefs(context);
        Deque<String> shown = new ArrayDeque<>(Arrays.asList(prefs.getString(SHOWN_IDS, "").split(",")));
        shown.remove("");
        if (shown.contains(id)) {
            return false;
        }
        shown.addLast(id);
        while (shown.size() > REMEMBERED) {
            shown.removeFirst();
        }
        prefs.edit().putString(SHOWN_IDS, String.join(",", shown)).apply();
        markChecked(context, createdAtMillis);
        ensureChannel(context);

        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (conversationId != null && !conversationId.isEmpty()) {
            open.putExtra(NotificationPollWorker.EXTRA_CONVERSATION, conversationId);
        } else {
            open.putExtra(NotificationPollWorker.EXTRA_OPEN_NOTIFICATIONS, true);
        }
        int requestCode = id.hashCode();
        PendingIntent tap = PendingIntent.getActivity(context, requestCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_paw)
                .setColor(ContextCompat.getColor(context, R.color.brand_coral))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setWhen(createdAtMillis)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tap)
                .build();
        NotificationManagerCompat.from(context).notify(requestCode, notification);
        return true;
    }

    private static void ensureChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    context.getString(R.string.push_channel), NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.push_channel_description));
            manager.createNotificationChannel(channel);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
