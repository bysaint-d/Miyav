package az.miyav.android.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import az.miyav.android.MiyavApplication;
import az.miyav.android.push.PhoneNotifications;
import az.miyav.common.error.AppException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.notification.Notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Background check for new MIYAV notifications, about every 15 minutes while signed in and right away when
 * the app opens. With push configured it only catches what push missed; without push it is the delivery
 * path. {@link PhoneNotifications} makes sure nothing is shown twice.
 */
public class NotificationPollWorker extends Worker {
    public static final String EXTRA_CONVERSATION = "az.miyav.notification.conversation";
    public static final String EXTRA_OPEN_NOTIFICATIONS = "az.miyav.notification.open";
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
        if (app.authService().current().isEmpty() || !PhoneNotifications.allowed(app)) {
            return Result.success();
        }
        long now = System.currentTimeMillis();
        // First run: start from a little before now so a fresh install does not replay old history.
        long lastShown = PhoneNotifications.lastShown(app, now - TimeUnit.HOURS.toMillis(2));
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
            PhoneNotifications.markChecked(app, lastShown);
            return Result.success();
        }
        fresh.sort(Comparator.comparing(Notification::createdAt));
        List<Notification> shown = fresh.size() > MAX_AT_ONCE ? fresh.subList(fresh.size() - MAX_AT_ONCE, fresh.size()) : fresh;
        for (Notification notification : shown) {
            PhoneNotifications.show(app, notification.id().toString(), notification.title(), notification.body(),
                    notification.conversationId().map(Object::toString).orElse(""),
                    notification.createdAt().toEpochMilli());
        }
        PhoneNotifications.markChecked(app, fresh.get(fresh.size() - 1).createdAt().toEpochMilli());
        return Result.success();
    }
}
