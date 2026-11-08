package az.miyav.android.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

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
     * An expired session sends the user back to sign-in instead of showing an error.
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
                } else {
                    error.accept(failure);
                }
            });
        });
    }

    static void signedOut(Fragment fragment, boolean expired) {
        MiyavApplication app = app(fragment);
        app.onSignedOut();
        if (expired) {
            Toast.makeText(fragment.requireContext(), R.string.auth_session_expired, Toast.LENGTH_LONG).show();
        }
        NavController nav = NavHostFragment.findNavController(fragment);
        nav.navigate(R.id.action_global_login);
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

    static String text(android.widget.EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
