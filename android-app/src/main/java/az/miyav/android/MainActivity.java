package az.miyav.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import az.miyav.android.auth.AuthLink;
import az.miyav.android.databinding.ActivityMainBinding;
import az.miyav.android.ui.Codes;
import az.miyav.common.error.AppException;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(system.left, system.top, system.right, Math.max(system.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        if (savedInstanceState == null) {
            handleAuthLink(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthLink(intent);
    }

    private void handleAuthLink(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null && "listing".equals(data.getHost()) && data.getLastPathSegment() != null) {
            intent.setData(null);
            try {
                java.util.UUID listingId = java.util.UUID.fromString(data.getLastPathSegment());
                ((MiyavApplication) getApplication()).setPendingListing(listingId);
            } catch (IllegalArgumentException ignored) {
                show(R.string.listing_unavailable);
            }
            return;
        }
        if (data == null || !BuildConfig.AUTH_REDIRECT.equals(data.getScheme() + "://" + data.getHost())) {
            return;
        }
        intent.setData(null);
        AuthLink link = AuthLink.parse(data.getEncodedFragment(), data.getEncodedQuery());
        if (link.kind() == AuthLink.Kind.ERROR || link.kind() == AuthLink.Kind.UNKNOWN) {
            show(Codes.text("auth.link_invalid"));
            return;
        }
        MiyavApplication app = (MiyavApplication) getApplication();
        app.io().execute(() -> {
            try {
                app.authService().completeLink(link.accessToken(), link.refreshToken(), link.expiresIn(), app.pendingAuthEmail().current());
                app.pendingAuthEmail().clear();
                runOnUiThread(() -> {
                    NavController nav = navController();
                    if (link.kind() == AuthLink.Kind.RECOVERY) {
                        nav.navigate(R.id.action_global_recovery);
                    } else {
                        nav.navigate(R.id.action_global_home);
                        show(R.string.auth_email_confirmed);
                    }
                });
            } catch (AppException exception) {
                runOnUiThread(() -> show(Codes.text(exception.code())));
            }
        });
    }

    private NavController navController() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        return host.getNavController();
    }

    private void show(int text) {
        Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG).show();
    }
}
