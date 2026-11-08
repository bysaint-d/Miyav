package az.miyav.android.sync;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import az.miyav.android.MainActivity;
import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.common.error.AppException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.notification.Notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shows new MIYAV notifications (admin broadcasts, messages, walk offers) as phone notifications. Runs about
 * every 15 minutes while signed in, and right away when the app opens. Only notifications newer than the
 * last one shown are posted, so nothing appears twice.
 */
public class NotificationPollWorker extends Worker {
    public static final String EXTRA_CONVERSATION = "az.miyav.notification.conversation";
    public static final String EXTRA_OPEN_NOTIFICATIONS = "az.miyav.notification.open";
    private static final String CHANNEL = "miyav_updates";
    private static final String PREFS = "miyav_push";
    private static final String LAST_SHOWN = "last_shown_ms";
    private static final int MAX_AT_ONCE = 5;

    public NotificationPollWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Periodic check plus one immediate run. */
    public static void schedule(Context context) {
        Constraints online = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager manager = WorkManager.getInstance(context);
        manager.enqueueUniquePeriodicWork("miyav-notifications", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(NotificationPollWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(online)
                        .build());
        manager.enqueueUniqueWork("miyav-notifications-now", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(NotificationPollWorker.class).setConstraints(online).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        MiyavApplication app = (MiyavApplication) getApplicationContext();
        if (app.authService().current().isEmpty() || !allowed(app)) {
            return Result.success();
        }
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        // First run: start from a little before now so a fresh install does not replay old history.
        long lastShown = prefs.getLong(LAST_SHOWN, now - TimeUnit.HOURS.toMillis(2));
        List<Notification> fresh = new ArrayList<>();
        try {
            AuthSession session = app.authService().requireSession();
            for (Notification notification : app.notificationService().list(session)) {
                if (notification.unread() && notification.createdAt().toEpochMilli() > lastShown) {
                    fresh.add(notification);
                }
            }
        } catch (AppException exception) {
            return Result.success();
        }
        if (fresh.isEmpty()) {
            prefs.edit().putLong(LAST_SHOWN, lastShown).apply();
            return Result.success();
        }
        fresh.sort(Comparator.comparing(Notification::createdAt));
        ensureChannel(app);
        NotificationManagerCompat manager = NotificationManagerCompat.from(app);
        List<Notification> shown = fresh.size() > MAX_AT_ONCE ? fresh.subList(fresh.size() - MAX_AT_ONCE, fresh.size()) : fresh;
        for (Notification notification : shown) {
            post(app, manager, notification);
        }
        Instant newest = fresh.get(fresh.size() - 1).createdAt();
        prefs.edit().putLong(LAST_SHOWN, newest.toEpochMilli()).apply();
        return Result.success();
    }

    private static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
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

    @SuppressWarnings("MissingPermission")
    private static void post(Context context, NotificationManagerCompat manager, Notification notification) {
        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notification.conversationId().ifPresentOrElse(
                id -> open.putExtra(EXTRA_CONVERSATION, id.toString()),
                () -> open.putExtra(EXTRA_OPEN_NOTIFICATIONS, true));
        int requestCode = notification.id().hashCode();
        PendingIntent tap = PendingIntent.getActivity(context, requestCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification shown = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_paw)
                .setColor(ContextCompat.getColor(context, R.color.brand_coral))
                .setContentTitle(notification.title())
                .setContentText(notification.body())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notification.body()))
                .setWhen(notification.createdAt().toEpochMilli())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tap)
                .build();
        manager.notify(requestCode, shown);
    }
}
