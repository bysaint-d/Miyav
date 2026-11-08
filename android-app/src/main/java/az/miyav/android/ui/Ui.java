package az.miyav.android.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.common.error.AppException;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class Ui {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Ui() {
    }

    static MiyavApplication app(Fragment fragment) {
        return (MiyavApplication) fragment.requireActivity().getApplication();
    }

    /**
     * Runs work off the main thread and delivers the result only while the fragment still has a view.
     * An expired or missing session never blocks browsing: the user stays in the app as a guest and is
     * offered sign-in.
     */
    static <T> void run(Fragment fragment, Supplier<T> work, Consumer<T> ok, Consumer<String> error) {
        MiyavApplication app = app(fragment);
        app.io().execute(() -> {
            T result = null;
            String code = null;
            try {
                result = work.get();
            } catch (AppException exception) {
                code = exception.code();
            } catch (RuntimeException exception) {
                code = "error.generic";
            }
            T value = result;
            String failure = code;
            MAIN.post(() -> {
                if (!fragment.isAdded() || fragment.getView() == null) {
                    return;
                }
                if (failure == null) {
                    ok.accept(value);
                } else if ("auth.session_expired".equals(failure)) {
                    signedOut(fragment, true);
                } else if ("auth.required".equals(failure)) {
                    AuthSheet.show(fragment, null);
                } else {
                    error.accept(failure);
                }
            });
        });
    }

    /**
     * The account is gone (sign-out or expiry). Account-only screens close; public screens stay, and the
     * home tabs re-render for guest mode through the auth state observer.
     */
    static void signedOut(Fragment fragment, boolean expired) {
        MiyavApplication app = app(fragment);
        if (expired) {
            app.auth().expire();
        }
        app.onSignedOut();
        NavController nav = NavHostFragment.findNavController(fragment);
        if (nav.getCurrentDestination() != null && ACCOUNT_SCREENS.contains(nav.getCurrentDestination().getId())) {
            nav.navigate(R.id.action_global_home);
        }
    }

    private static final java.util.Set<Integer> ACCOUNT_SCREENS = java.util.Set.of(
            R.id.petEditorFragment, R.id.notificationsFragment, R.id.passwordFragment, R.id.walkEditorFragment,
            R.id.inboxFragment, R.id.chatFragment, R.id.myPetsListFragment);

    /** Opens a chat; any of the ids may be empty (the chat screen resolves the rest). */
    static void openChat(Fragment fragment, String conversationId, String listingId, String withUserId, String title) {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("conversationId", conversationId == null ? "" : conversationId);
        args.putString("listingId", listingId == null ? "" : listingId);
        args.putString("withUserId", withUserId == null ? "" : withUserId);
        args.putString("title", title == null ? "" : title);
        nav(fragment).navigate(R.id.action_global_chat, args);
    }

    static NavController nav(Fragment fragment) {
        return NavHostFragment.findNavController(fragment);
    }

    static void snack(View view, int text) {
        if (view != null) {
            Snackbar.make(view, text, Snackbar.LENGTH_LONG).show();
        }
    }

    static void snack(View view, String code) {
        snack(view, Codes.text(code));
    }

    static void snackText(View view, CharSequence text) {
        if (view != null) {
            Snackbar.make(view, text, Snackbar.LENGTH_LONG).show();
        }
    }

    static String text(android.widget.EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
