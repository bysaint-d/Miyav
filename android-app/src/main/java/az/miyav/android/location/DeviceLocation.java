package az.miyav.android.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import java.util.List;
import java.util.function.Consumer;

/** One-shot device location using the platform providers (no Google Play services dependency). */
public final class DeviceLocation {
    public enum Failure { PERMISSION, DISABLED, UNAVAILABLE }

    public interface Callback {
        void onLocation(Location location);

        void onFailure(Failure failure);
    }

    private DeviceLocation() {
    }

    public static boolean hasFine(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasAny(Context context) {
        return hasFine(context)
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean enabled(Context context) {
        LocationManager manager = context.getSystemService(LocationManager.class);
        return manager != null && LocationManagerCompat.isLocationEnabled(manager);
    }

    public static void current(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        if (!hasAny(app)) {
            callback.onFailure(Failure.PERMISSION);
            return;
        }
        LocationManager manager = app.getSystemService(LocationManager.class);
        if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
            callback.onFailure(Failure.DISABLED);
            return;
        }
        String provider = bestProvider(app, manager);
        if (provider == null) {
            callback.onFailure(Failure.UNAVAILABLE);
            return;
        }
        try {
            Location last = manager.getLastKnownLocation(provider);
            if (last != null && System.currentTimeMillis() - last.getTime() < 2 * 60 * 1000) {
                callback.onLocation(last);
                return;
            }
            CancellationSignal cancel = new CancellationSignal();
            Handler main = new Handler(Looper.getMainLooper());
            boolean[] done = {false};
            Consumer<Location> deliver = location -> {
                if (done[0]) {
                    return;
                }
                done[0] = true;
                if (location != null) {
                    callback.onLocation(location);
                } else if (last != null) {
                    callback.onLocation(last);
                } else {
                    callback.onFailure(Failure.UNAVAILABLE);
                }
            };
            main.postDelayed(() -> {
                cancel.cancel();
                deliver.accept(null);
            }, 15_000);
            LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(app), deliver::accept);
        } catch (SecurityException exception) {
            callback.onFailure(Failure.PERMISSION);
        }
    }

    public static String bestProvider(Context context, LocationManager manager) {
        if (hasFine(context) && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER;
        }
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        List<String> providers = manager.getProviders(true);
        return providers.contains(LocationManager.PASSIVE_PROVIDER) ? LocationManager.PASSIVE_PROVIDER : null;
    }
}
