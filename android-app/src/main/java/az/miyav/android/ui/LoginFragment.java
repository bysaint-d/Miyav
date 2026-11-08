package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import az.miyav.android.AppLocale;
import az.miyav.android.BuildConfig;
import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentLoginBinding;

public class LoginFragment extends Fragment {
    private FragmentLoginBinding binding;
    private LoginViewModel model;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        MiyavApplication app = Ui.app(this);
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new LoginViewModel(app.authService(), app.pendingAuthEmail(), app.io(), BuildConfig.AUTH_REDIRECT));
            }
        }).get(LoginViewModel.class);
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                applyMode(checkedId == R.id.modeRegister);
            }
        });
        applyMode(binding.modeGroup.getCheckedButtonId() == R.id.modeRegister);
        binding.submitButton.setOnClickListener(view -> submit());
        binding.passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && !isRegister()) {
                submit();
                return true;
            }
            return false;
        });
        binding.confirmInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        binding.forgotButton.setOnClickListener(view -> {
            clearErrors();
            model.reset(Ui.text(binding.emailInput));
        });
        String locale = AppLocale.current(requireContext());
        binding.languageGroup.check(switch (locale) {
            case "en" -> R.id.languageEn;
            case "ru" -> R.id.languageRu;
            default -> R.id.languageAz;
        });
        binding.languageGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String tag = checkedId == R.id.languageEn ? "en" : checkedId == R.id.languageRu ? "ru" : "az";
            if (!tag.equals(locale)) {
                AppLocale.select(requireActivity(), tag);
            }
        });
        model.state().observe(getViewLifecycleOwner(), this::render);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private boolean isRegister() {
        return binding.modeGroup.getCheckedButtonId() == R.id.modeRegister;
    }

    private void applyMode(boolean register) {
        binding.nameLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        binding.confirmLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        binding.forgotButton.setVisibility(register ? View.GONE : View.VISIBLE);
        binding.submitButton.setText(register ? R.string.auth_register : R.string.auth_login);
        binding.passwordInput.setImeOptions(register ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        binding.messageView.setText("");
        clearErrors();
    }

    private void submit() {
        clearErrors();
        String email = Ui.text(binding.emailInput).trim();
        String password = Ui.text(binding.passwordInput);
        if (isRegister()) {
            String name = Ui.text(binding.nameInput).trim();
            if (name.length() < 2) {
                binding.nameLayout.setError(getString(R.string.validation_name));
                return;
            }
            if (!password.equals(Ui.text(binding.confirmInput))) {
                binding.confirmLayout.setError(getString(R.string.auth_password_mismatch));
                return;
            }
            model.register(email, password, name);
        } else {
            model.login(email, password);
        }
    }

    private void render(LoginViewModel.LoginState state) {
        boolean loading = state.kind == LoginViewModel.LoginState.Kind.LOADING;
        binding.submitButton.setEnabled(!loading);
        binding.forgotButton.setEnabled(!loading);
        binding.modeGroup.setEnabled(!loading);
        binding.progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        binding.messageView.setTextColor(requireContext().getColor(
                state.kind == LoginViewModel.LoginState.Kind.ERROR ? R.color.error : R.color.text_secondary));
        switch (state.kind) {
            case SUCCESS -> Ui.nav(this).navigate(R.id.action_global_home);
            case INFO -> binding.messageView.setText(Codes.text(state.code));
            case ERROR -> showError(state.code);
            default -> {
            }
        }
    }

    private void showError(String code) {
        switch (code) {
            case "validation.email" -> binding.emailLayout.setError(getString(R.string.validation_email));
            case "validation.password" -> binding.passwordLayout.setError(getString(R.string.validation_password));
            case "validation.name" -> binding.nameLayout.setError(getString(R.string.validation_name));
            default -> binding.messageView.setText(Codes.text(code));
        }
    }

    private void clearErrors() {
        binding.nameLayout.setError(null);
        binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null);
        binding.confirmLayout.setError(null);
    }
}
