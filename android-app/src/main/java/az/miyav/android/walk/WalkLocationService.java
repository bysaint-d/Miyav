package az.miyav.android.walk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import az.miyav.android.MainActivity;
import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.location.DeviceLocation;

/**
 * Foreground service that runs only during an active walk. It publishes the device position every
 * 20 s / 20 m and sends a heartbeat every minute so a standing walker does not expire on the server.
 */
public final class WalkLocationService extends Service implements LocationListener {
    public static final String CHANNEL = "miyav_walk";
    public static final String ACTION_PAUSE = "az.miyav.walk.PAUSE";
    public static final String ACTION_END = "az.miyav.walk.END";
    private static final int NOTIFICATION_ID = 4107;
    private static final long INTERVAL_MS = 20_000;
    private static final float MIN_DISTANCE_M = 20f;
    private static final long HEARTBEAT_MS = 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Location last;
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (last != null) {
                publish(last);
            }
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MiyavApplication app = (MiyavApplication) getApplication();
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            app.walkTracker().pause(code -> { });
            return START_NOT_STICKY;
        }
        if (ACTION_END.equals(action)) {
            app.walkTracker().end(code -> { });
            return START_NOT_STICKY;
        }
        if (!DeviceLocation.hasAny(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();
        requestUpdates();
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, HEARTBEAT_MS);
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.walk_notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        int immutable = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), immutable);
        PendingIntent pause = PendingIntent.getService(this, 1, new Intent(this, WalkLocationService.class).setAction(ACTION_PAUSE), immutable);
        PendingIntent end = PendingIntent.getService(this, 2, new Intent(this, WalkLocationService.class).setAction(ACTION_END), immutable);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_paw)
                .setColor(ContextCompat.getColor(this, R.color.brand_coral))
                .setContentTitle(getString(R.string.walk_active))
                .setContentText(getString(R.string.walk_notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(R.drawable.ic_pause, getString(R.string.walk_pause), pause)
                .addAction(R.drawable.ic_stop, getString(R.string.walk_end), end)
                .build();
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type);
    }

    private void requestUpdates() {
        LocationManager manager = getSystemService(LocationManager.class);
        if (manager == null) {
            return;
        }
        String provider = DeviceLocation.bestProvider(this, manager);
        if (provider == null) {
            return;
        }
        try {
            manager.removeUpdates(this);
            manager.requestLocationUpdates(provider, INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper());
            Location known = manager.getLastKnownLocation(provider);
            if (known != null && last == null) {
                last = known;
            }
        } catch (SecurityException exception) {
            stopSelf();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        last = location;
        publish(location);
    }

    private void publish(Location location) {
        MiyavApplication app = (MiyavApplication) getApplication();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        app.io().execute(() -> app.walkTracker().publish(latitude, longitude));
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(heartbeat);
        LocationManager manager = getSystemService(LocationManager.class);
        if (manager != null) {
            manager.removeUpdates(this);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
