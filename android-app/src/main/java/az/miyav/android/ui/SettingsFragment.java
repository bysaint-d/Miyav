package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.AppLocale;
import az.miyav.android.BuildConfig;
import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentSettingsBinding;
import az.miyav.common.error.AppException;
import az.miyav.common.locale.LocaleCode;

/** Everything that is not the member's own profile: shortcuts, language, legal pages, sign-out and deletion. */
public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private boolean bindingLanguage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        binding.versionView.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        binding.myPetsButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_my_pets));
        binding.passwordButton.setVisibility(Ui.app(this).auth().usesPassword() ? View.VISIBLE : View.GONE);
        binding.passwordButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_settings_to_password));
        binding.blockedButton.setOnClickListener(view -> SafetyActions.manage(this));
        binding.messagesButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_inbox));
        binding.notificationsButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_settings_to_notifications));
        binding.favoritesButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_favorites));
        binding.helpButton.setOnClickListener(view -> InfoFragment.open(this, InfoFragment.HELP));
        binding.termsButton.setOnClickListener(view -> InfoFragment.open(this, InfoFragment.TERMS));
        binding.privacyButton.setOnClickListener(view -> InfoFragment.open(this, InfoFragment.PRIVACY));
        binding.logoutButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.auth_logout_confirm)
                .setPositiveButton(R.string.auth_logout, (dialog, which) -> logout())
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        binding.deleteAccountButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_delete_title)
                .setMessage(R.string.account_delete_body)
                .setPositiveButton(R.string.account_delete_confirm, (dialog, which) -> deleteAccount())
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        bindLanguage();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void bindLanguage() {
        String current = AppLocale.current(requireContext());
        bindingLanguage = true;
        binding.languageGroup.check(switch (current) {
            case "en" -> R.id.languageEn;
            case "ru" -> R.id.languageRu;
            default -> R.id.languageAz;
        });
        bindingLanguage = false;
        binding.languageGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || bindingLanguage) {
                return;
            }
            String tag = checkedId == R.id.languageEn ? "en" : checkedId == R.id.languageRu ? "ru" : "az";
            if (tag.equals(current)) {
                return;
            }
            MiyavApplication app = Ui.app(this);
            app.io().execute(() -> {
                try {
                    app.userService().updateLocale(app.auth().requireSession(), LocaleCode.fromTag(tag));
                } catch (AppException ignored) {
                    // The device language still changes; the profile syncs on the next successful save.
                }
            });
            AppLocale.select(requireActivity(), tag);
        });
    }

    private void logout() {
        MiyavApplication app = Ui.app(this);
        busy(true);
        Ui.run(this, () -> {
            app.onSignedOut();
            try {
                app.auth().signOut();
            } catch (AppException ignored) {
                // The local session is cleared even when the server cannot be reached.
            }
            return Boolean.TRUE;
        }, done -> leave(false), code -> leave(false));
    }

    private void deleteAccount() {
        MiyavApplication app = Ui.app(this);
        busy(true);
        Ui.run(this, () -> {
            app.auth().deleteAccount();
            return Boolean.TRUE;
        }, done -> leave(true), code -> {
            busy(false);
            Ui.snack(binding.getRoot(), code);
        });
    }

    /** Back to the public home, which re-renders every tab for a guest. */
    private void leave(boolean deleted) {
        Ui.app(this).onSignedOut();
        Ui.nav(this).navigate(R.id.action_global_home);
        if (deleted) {
            Ui.snack(requireActivity().findViewById(android.R.id.content), R.string.account_deleted);
        }
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.logoutButton.setEnabled(!busy);
        binding.deleteAccountButton.setEnabled(!busy);
    }
}
