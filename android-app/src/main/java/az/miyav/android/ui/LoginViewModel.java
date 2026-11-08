package az.miyav.android.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import az.miyav.android.data.PendingAuthEmail;
import az.miyav.application.AuthService;
import az.miyav.application.AuthStateManager;
import az.miyav.common.error.AppException;

import java.util.concurrent.ExecutorService;

public final class LoginViewModel extends ViewModel {
    private final AuthStateManager auth;
    private final AuthService authService;
    private final PendingAuthEmail pendingEmail;
    private final ExecutorService executor;
    private final String redirect;
    private final MutableLiveData<LoginState> state = new MutableLiveData<>(LoginState.idle());

    public LoginViewModel(AuthStateManager auth, AuthService authService, PendingAuthEmail pendingEmail,
                          ExecutorService executor, String redirect) {
        this.auth = auth;
        this.authService = authService;
        this.pendingEmail = pendingEmail;
        this.executor = executor;
        this.redirect = redirect;
    }

    public LiveData<LoginState> state() {
        return state;
    }

    /** Marks a success as handled so a recreated screen does not navigate twice. */
    public void acknowledge() {
        state.setValue(LoginState.idle());
    }

    public void login(String email, String password) {
        submit(() -> {
            auth.login(email, password);
            return LoginState.success();
        });
    }

    public void register(String email, String password, String fullName) {
        submit(() -> {
            pendingEmail.remember(email);
            try {
                auth.register(email, password, fullName, redirect);
                return LoginState.success();
            } catch (AppException exception) {
                if ("auth.confirmation_required".equals(exception.code())) {
                    return LoginState.info("auth.check_email");
                }
                throw exception;
            }
        });
    }

    public void reset(String email) {
        submit(() -> {
            pendingEmail.remember(email);
            authService.requestPasswordReset(email, redirect);
            return LoginState.info("auth.reset_sent");
        });
    }

    private void submit(java.util.function.Supplier<LoginState> action) {
        if (state.getValue() != null && state.getValue().kind == LoginState.Kind.LOADING) {
            return;
        }
        state.setValue(LoginState.loading());
        executor.execute(() -> {
            try {
                state.postValue(action.get());
            } catch (AppException exception) {
                state.postValue(LoginState.error(exception.code()));
            } catch (RuntimeException exception) {
                state.postValue(LoginState.error("error.generic"));
            }
        });
    }

    public static final class LoginState {
        public enum Kind { IDLE, LOADING, SUCCESS, ERROR, INFO }

        public final Kind kind;
        public final String code;

        private LoginState(Kind kind, String code) {
            this.kind = kind;
            this.code = code;
        }

        public static LoginState idle() {
            return new LoginState(Kind.IDLE, "");
        }

        public static LoginState loading() {
            return new LoginState(Kind.LOADING, "");
        }

        public static LoginState success() {
            return new LoginState(Kind.SUCCESS, "");
        }

        public static LoginState error(String code) {
            return new LoginState(Kind.ERROR, code);
        }

        public static LoginState info(String code) {
            return new LoginState(Kind.INFO, code);
        }
    }
}
