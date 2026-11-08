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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import az.miyav.android.auth.AuthLink;
import az.miyav.android.databinding.ActivityMainBinding;
import az.miyav.android.sync.NotificationPollWorker;
import az.miyav.android.ui.Codes;
import az.miyav.common.error.AppException;
import az.miyav.domain.auth.AuthState;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private AuthState lastAuthState;

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
        MiyavApplication app = (MiyavApplication) getApplication();
        lastAuthState = app.auth().state();
        app.authState().observe(this, this::onAuthState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthLink(intent);
    }

    private void handleAuthLink(Intent intent) {
        if (intent != null && (intent.hasExtra(NotificationPollWorker.EXTRA_CONVERSATION)
                || intent.hasExtra(NotificationPollWorker.EXTRA_OPEN_NOTIFICATIONS))) {
            String conversation = intent.getStringExtra(NotificationPollWorker.EXTRA_CONVERSATION);
            intent.removeExtra(NotificationPollWorker.EXTRA_CONVERSATION);
            intent.removeExtra(NotificationPollWorker.EXTRA_OPEN_NOTIFICATIONS);
            ((MiyavApplication) getApplication()).setPendingTap(conversation == null ? "" : conversation);
            return;
        }
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
        MiyavApplication app = (MiyavApplication) getApplication();
        if (link.kind() == AuthLink.Kind.ERROR || link.kind() == AuthLink.Kind.UNKNOWN) {
            boolean fromGoogle = !app.pendingGoogle().take().isEmpty();
            app.auth().cancelAuthentication();
            show(Codes.text(fromGoogle ? "auth.google_unavailable" : "auth.link_invalid"));
            return;
        }
        if (link.kind() == AuthLink.Kind.CODE) {
            completeGoogle(app, link.code());
            return;
        }
        app.io().execute(() -> {
            try {
                app.auth().completeLink(link.accessToken(), link.refreshToken(), link.expiresIn(), app.pendingAuthEmail().current());
                app.pendingAuthEmail().clear();
                runOnUiThread(() -> {
                    if (link.kind() == AuthLink.Kind.RECOVERY) {
                        navController().navigate(R.id.action_global_recovery);
                    } else {
                        returnFromSignIn();
                        show(R.string.auth_email_confirmed);
                    }
                });
            } catch (AppException exception) {
                runOnUiThread(() -> show(Codes.text(exception.code())));
            }
        });
    }

    /** Finishes Google sign-in. Only a code for the verifier this device created is accepted. */
    private void completeGoogle(MiyavApplication app, String code) {
        String verifier = app.pendingGoogle().take();
        if (verifier.isEmpty()) {
            app.auth().cancelAuthentication();
            show(Codes.text("auth.link_invalid"));
            return;
        }
        app.io().execute(() -> {
            try {
                app.auth().completeGoogle(code, verifier);
                runOnUiThread(() -> {
                    returnFromSignIn();
                    show(R.string.auth_signed_in);
                });
            } catch (AppException exception) {
                runOnUiThread(() -> show(Codes.text(exception.code())));
            }
        });
    }

    /** Leaves the sign-in form if it is open; the screen below resumes its pending action. */
    private void returnFromSignIn() {
        NavController nav = navController();
        if (nav.getCurrentDestination() != null && nav.getCurrentDestination().getId() == R.id.loginFragment) {
            nav.popBackStack();
        }
    }

    private void onAuthState(AuthState state) {
        MiyavApplication app = (MiyavApplication) getApplication();
        if (state == AuthState.SESSION_EXPIRED && lastAuthState != AuthState.SESSION_EXPIRED) {
            Snackbar.make(binding.getRoot(), R.string.guest_session_expired, Snackbar.LENGTH_LONG).show();
        }
        if (state == AuthState.AUTHENTICATED && lastAuthState != AuthState.AUTHENTICATED) {
            NotificationPollWorker.schedule(this);
            app.io().execute(() -> {
                int count = app.favorites().guestCount();
                if (count > 0 && app.claimFavoritesOffer()) {
                    runOnUiThread(() -> offerFavoritesSync(app, count));
                }
            });
        }
        lastAuthState = state;
    }

    /** Guest favourites move to the account only after the user agrees. */
    private void offerFavoritesSync(MiyavApplication app, int count) {
        if (isFinishing()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.favorites_sync_title)
                .setMessage(getResources().getQuantityString(R.plurals.favorites_sync_body, count, count))
                .setPositiveButton(R.string.favorites_sync_yes, (dialog, which) -> app.io().execute(() -> {
                    try {
                        var result = app.favorites().syncGuestFavorites(app.auth().requireSession());
                        runOnUiThread(() -> Snackbar.make(binding.getRoot(), getString(R.string.favorites_synced,
                                result.added(), result.alreadySaved(), result.unavailable()), Snackbar.LENGTH_LONG).show());
                    } catch (AppException exception) {
                        runOnUiThread(() -> show(Codes.text(exception.code())));
                    }
                }))
                .setNegativeButton(R.string.favorites_sync_no, null)
                .show();
    }

    private NavController navController() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        return host.getNavController();
    }

    private void show(int text) {
        Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG).show();
    }
}
