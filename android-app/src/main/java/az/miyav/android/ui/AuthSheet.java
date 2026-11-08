package az.miyav.android.ui;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import az.miyav.android.BuildConfig;
import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.SheetAuthBinding;
import az.miyav.domain.auth.ProtectedAction;

/** The MIYAV sign-in sheet shown when a guest reaches an account-only action. */
public class AuthSheet extends BottomSheetDialogFragment {
    private static final String TAG = "miyav-auth-sheet";
    private static final String ARG_ACTION = "action";
    private SheetAuthBinding binding;
    private boolean proceeding;

    static void show(Fragment host, ProtectedAction action) {
        if (host.getChildFragmentManager().findFragmentByTag(TAG) != null || host.getChildFragmentManager().isStateSaved()) {
            return;
        }
        AuthSheet sheet = new AuthSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ACTION, action == null ? "" : action.name());
        sheet.setArguments(args);
        sheet.show(host.getChildFragmentManager(), TAG);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetAuthBinding.inflate(inflater, container, false);
        ProtectedAction action = ProtectedAction.fromName(requireArguments().getString(ARG_ACTION));
        binding.messageView.setText(Gate.prompt(action));
        binding.emailButton.setOnClickListener(view -> openForm(false));
        binding.registerButton.setOnClickListener(view -> openForm(true));
        binding.googleButton.setOnClickListener(view -> google());
        binding.telegramButton.setOnClickListener(view -> telegram());
        binding.laterButton.setOnClickListener(view -> dismiss());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (!proceeding && isAdded()) {
            Ui.app(this).auth().cancelAuthentication();
        }
        super.onDismiss(dialog);
    }

    private void openForm(boolean register) {
        NavController nav = NavHostFragment.findNavController(this);
        proceeding = true;
        Ui.app(this).auth().beginAuthentication();
        Bundle args = new Bundle();
        args.putString("mode", register ? "register" : "login");
        dismiss();
        nav.navigate(R.id.action_global_sign_in, args);
    }

    private void google() {
        MiyavApplication app = Ui.app(this);
        busy(true);
        Ui.run(this, () -> app.authService().beginGoogle(BuildConfig.AUTH_REDIRECT), signIn -> {
            app.pendingGoogle().remember(signIn.verifier());
            app.auth().beginAuthentication();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(signIn.authorizeUri().toString())));
                proceeding = true;
                dismiss();
            } catch (ActivityNotFoundException exception) {
                busy(false);
                binding.noteView.setText(R.string.auth_unavailable);
                binding.noteView.setVisibility(View.VISIBLE);
            }
        }, code -> {
            busy(false);
            binding.noteView.setText(Codes.text(code));
            binding.noteView.setVisibility(View.VISIBLE);
        });
    }

    private void telegram() {
        if (!Ui.app(this).telegram().available()) {
            binding.noteView.setText(R.string.guest_telegram_unavailable);
            binding.noteView.setVisibility(View.VISIBLE);
        }
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.googleButton.setEnabled(!busy);
        binding.emailButton.setEnabled(!busy);
        binding.registerButton.setEnabled(!busy);
    }
}
