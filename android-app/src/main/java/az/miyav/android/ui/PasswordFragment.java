package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentPasswordBinding;

/** Changes the password from the profile, or sets a new one after opening a recovery link. */
public class PasswordFragment extends Fragment {
    private FragmentPasswordBinding binding;
    private boolean recovery;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        recovery = getArguments() != null && getArguments().getBoolean("recovery", false);
        if (recovery) {
            binding.toolbar.setTitle(R.string.auth_set_new_password_title);
            binding.toolbar.setNavigationIcon(null);
            binding.currentLayout.setVisibility(View.GONE);
        } else {
            binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        }
        binding.saveButton.setOnClickListener(view -> save());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void save() {
        binding.currentLayout.setError(null);
        binding.newLayout.setError(null);
        binding.confirmLayout.setError(null);
        String current = Ui.text(binding.currentInput);
        String next = Ui.text(binding.newInput);
        if (!recovery && current.isEmpty()) {
            binding.currentLayout.setError(getString(R.string.auth_current_password));
            return;
        }
        if (!next.equals(Ui.text(binding.confirmInput))) {
            binding.confirmLayout.setError(getString(R.string.auth_password_mismatch));
            return;
        }
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            if (recovery) {
                app.authService().setRecoveredPassword(next);
            } else {
                app.authService().changePassword(current, next);
            }
            return Boolean.TRUE;
        }, done -> {
            busy(false);
            Ui.snack(binding.getRoot(), R.string.auth_password_changed);
            if (recovery) {
                Ui.nav(this).navigate(R.id.action_global_home);
            } else {
                Ui.nav(this).popBackStack();
            }
        }, code -> {
            busy(false);
            switch (code) {
                case "auth.current_password" -> binding.currentLayout.setError(getString(R.string.auth_current_password));
                case "validation.password", "auth.same_password" -> binding.newLayout.setError(getString(Codes.text(code)));
                default -> binding.messageView.setText(Codes.text(code));
            }
        });
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.saveButton.setEnabled(!busy);
    }
}
