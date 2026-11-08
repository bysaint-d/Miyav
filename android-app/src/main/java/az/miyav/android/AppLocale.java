package az.miyav.android;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/** Persists and applies the in-app language (az, en, ru), independent of the system language. */
public final class AppLocale {
    private static final String PREFERENCES = "miyav";
    private static final String KEY = "locale";

    private AppLocale() {
    }

    public static String current(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, "az");
    }

    /** Builds a context whose resources use the stored language. */
    public static Context wrap(Context base) {
        Locale locale = Locale.forLanguageTag(current(base));
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(locale));
        return base.createConfigurationContext(configuration);
    }

    public static void select(Activity activity, String tag) {
        activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit();
        apply(activity, tag);
        activity.recreate();
    }

    /** Keeps the system per-app language setting in sync (Android 13+ settings screen). */
    public static void apply(Context context, String tag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            LocaleList wanted = new LocaleList(Locale.forLanguageTag(tag));
            if (manager != null && !wanted.equals(manager.getApplicationLocales())) {
                manager.setApplicationLocales(wanted);
            }
            return;
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }
}
