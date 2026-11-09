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

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentProfileEditBinding;
import az.miyav.domain.location.Districts;
import az.miyav.domain.user.PhoneNumbers;
import az.miyav.domain.user.User;

import java.util.ArrayList;
import java.util.List;

/** Name, photo, city and contact details. Saving returns to the profile. */
public class ProfileEditFragment extends Fragment {
    private FragmentProfileEditBinding binding;
    private Choice<String> city;
    private Choice<String> district;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onAvatarPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileEditBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        city = new Choice<>(binding.cityInput, Labels.CITIES, value -> value, Labels.CITIES.get(0));
        district = new Choice<>(binding.districtInput, districtsOf(city.value(), null),
                value -> value == null ? getString(R.string.district_none) : value, null);
        city.onChange(() -> district.replace(districtsOf(city.value(), null), null));
        binding.saveButton.setOnClickListener(view -> save());
        binding.avatarButton.setOnClickListener(view -> pickAvatar());
        binding.avatarView.setOnClickListener(view -> pickAvatar());
        if (savedInstanceState == null) {
            load();
        }
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
            return app.userService().current(app.auth().requireSession());
        }, user -> {
            busy(false);
            fill(user);
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void fill(User user) {
        binding.nameInput.setText(user.fullName());
        binding.phoneInput.setText(user.phone());
        binding.bioInput.setText(user.bio());
        binding.phonePublicSwitch.setChecked(user.phonePublic());
        city.select(user.city());
        String current = user.district().isEmpty() ? null : user.district();
        district.replace(districtsOf(user.city(), current), current);
        Ui.app(this).photos().avatar(binding.avatarView, user);
    }

    private void save() {
        String name = Ui.text(binding.nameInput).trim();
        binding.nameLayout.setError(null);
        if (name.length() < 2 || name.length() > 100) {
            binding.nameLayout.setError(getString(R.string.validation_name));
            return;
        }
        String districtValue = district.value() == null ? "" : district.value();
        String phone = Ui.text(binding.phoneInput).trim();
        String bio = Ui.text(binding.bioInput).trim();
        boolean phonePublic = binding.phonePublicSwitch.isChecked();
        String cityValue = city.value();
        binding.phoneLayout.setError(null);
        if (!PhoneNumbers.valid(phone)) {
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
            return app.userService().updateProfile(app.auth().requireSession(), name, cityValue, districtValue, phone, bio, phonePublic);
        }, user -> {
            Ui.snack(requireActivity().findViewById(android.R.id.content), R.string.profile_saved);
            Ui.nav(this).popBackStack();
        }, code -> {
            busy(false);
            if (code.startsWith("validation.phone")) {
                binding.phoneLayout.setError(getString(Codes.text(code)));
            } else {
                binding.messageView.setText(Codes.text(code));
            }
        });
    }

    /** "Not chosen" first, then the city's districts; a saved district from before the list is kept. */
    private static List<String> districtsOf(String cityValue, String saved) {
        List<String> options = new ArrayList<>();
        options.add(null);
        options.addAll(Districts.of(cityValue));
        if (saved != null && !options.contains(saved)) {
            options.add(saved);
        }
        return options;
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
            return app.userService().updateAvatar(app.auth().requireSession(), jpeg);
        }, user -> {
            busy(false);
            Ui.app(this).photos().avatar(binding.avatarView, user);
            binding.messageView.setText(R.string.profile_saved);
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.saveButton.setEnabled(!busy);
        binding.avatarButton.setEnabled(!busy);
    }
}
