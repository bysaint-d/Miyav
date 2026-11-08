package az.miyav.android.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.AppLocale;
import az.miyav.android.BuildConfig;
import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentProfileBinding;
import az.miyav.common.error.AppException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.user.User;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private Choice<String> city;
    private boolean bindingLanguage;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onAvatarPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        city = new Choice<>(binding.cityInput, Labels.CITIES, value -> value, Labels.CITIES.get(0));
        binding.versionView.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        binding.saveButton.setOnClickListener(view -> save());
        binding.avatarButton.setOnClickListener(view -> pickAvatar());
        binding.avatarView.setOnClickListener(view -> pickAvatar());
        binding.notificationsButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_home_to_notifications));
        binding.passwordButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_home_to_password));
        binding.logoutButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.auth_logout_confirm)
                .setPositiveButton(R.string.auth_logout, (dialog, which) -> logout())
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        bindLanguage();
        load();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.userService().current(app.authService().requireSession());
        }, user -> {
            busy(false);
            fill(user);
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void fill(User user) {
        binding.nameView.setText(user.fullName());
        binding.emailView.setText(getString(R.string.format_pair, user.email(), Labels.role(requireContext(), user.role())));
        binding.nameInput.setText(user.fullName());
        binding.districtInput.setText(user.district());
        binding.phoneInput.setText(user.phone());
        binding.bioInput.setText(user.bio());
        binding.phonePublicSwitch.setChecked(user.phonePublic());
        city.select(user.city());
        Ui.app(this).photos().avatar(binding.avatarView, user);
    }

    private void save() {
        String name = Ui.text(binding.nameInput).trim();
        binding.nameLayout.setError(null);
        if (name.length() < 2 || name.length() > 100) {
            binding.nameLayout.setError(getString(R.string.validation_name));
            return;
        }
        String district = Ui.text(binding.districtInput);
        String phone = Ui.text(binding.phoneInput).trim();
        String bio = Ui.text(binding.bioInput).trim();
        boolean phonePublic = binding.phonePublicSwitch.isChecked();
        String cityValue = city.value();
        binding.phoneLayout.setError(null);
        if (!az.miyav.domain.user.PhoneNumbers.valid(phone)) {
            binding.phoneLayout.setError(getString(R.string.validation_phone));
            return;
        }
        if (phonePublic && phone.isEmpty()) {
            binding.phoneLayout.setError(getString(R.string.validation_phone_required));
            return;
        }
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.userService().updateProfile(app.authService().requireSession(), name, cityValue, district, phone, bio, phonePublic);
        }, user -> {
            busy(false);
            fill(user);
            binding.messageView.setText(R.string.profile_saved);
        }, code -> {
            busy(false);
            if (code.startsWith("validation.phone")) {
                binding.phoneLayout.setError(getString(Codes.text(code)));
            } else {
                binding.messageView.setText(Codes.text(code));
            }
        });
    }

    private void pickAvatar() {
        picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void onAvatarPicked(@Nullable Uri uri) {
        if (uri == null || binding == null) {
            return;
        }
        busy(true);
        Context context = requireContext().getApplicationContext();
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            byte[] jpeg = JpegEncoder.fromUri(context, uri, 800);
            return app.userService().updateAvatar(app.authService().requireSession(), jpeg);
        }, user -> {
            busy(false);
            fill(user);
            binding.messageView.setText(R.string.profile_saved);
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
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
                    app.userService().updateLocale(app.authService().requireSession(), LocaleCode.fromTag(tag));
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
            try {
                app.authService().logout();
            } catch (AppException ignored) {
                // The local session is cleared even when the server cannot be reached.
            }
            return Boolean.TRUE;
        }, done -> Ui.signedOut(this, false), code -> Ui.signedOut(this, false));
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.saveButton.setEnabled(!busy);
        binding.avatarButton.setEnabled(!busy);
        binding.logoutButton.setEnabled(!busy);
    }
}
