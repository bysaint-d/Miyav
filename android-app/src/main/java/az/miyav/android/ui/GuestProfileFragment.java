package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import az.miyav.android.AppLocale;
import az.miyav.android.BuildConfig;
import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentGuestProfileBinding;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;

/** Profile tab for guests: account entry points plus everything that works without an account. */
public class GuestProfileFragment extends Fragment implements HomeFragment.GuestTab {
    private FragmentGuestProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGuestProfileBinding.inflate(inflater, container, false);
        binding.signInButton.setOnClickListener(view -> Gate.require(this, ProtectedAction.EDIT_PROFILE));
        binding.registerButton.setOnClickListener(view -> register());
        binding.favoritesButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_favorites));
        binding.helpButton.setOnClickListener(view -> InfoFragment.open(this, InfoFragment.HELP));
        binding.termsButton.setOnClickListener(view -> InfoFragment.open(this, InfoFragment.TERMS));
        binding.privacyButton.setOnClickListener(view -> InfoFragment.open(this, InfoFragment.PRIVACY));
        binding.versionView.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        bindLanguage();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        MiyavApplication app = Ui.app(this);
        Ui.run(this, () -> app.favorites().guestCount(), count -> binding.favoritesButton.setText(count == 0
                ? getString(R.string.favorites_title)
                : getString(R.string.favorites_title_count, count)), code -> {
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void register() {
        MiyavApplication app = Ui.app(this);
        app.auth().request(PendingAction.of(ProtectedAction.EDIT_PROFILE));
        app.auth().beginAuthentication();
        Bundle args = new Bundle();
        args.putString("mode", "register");
        Ui.nav(this).navigate(R.id.action_global_sign_in, args);
    }

    private void bindLanguage() {
        String current = AppLocale.current(requireContext());
        binding.languageGroup.check(switch (current) {
            case "en" -> R.id.languageEn;
            case "ru" -> R.id.languageRu;
            default -> R.id.languageAz;
        });
        binding.languageGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String tag = checkedId == R.id.languageEn ? "en" : checkedId == R.id.languageRu ? "ru" : "az";
            if (!tag.equals(current)) {
                AppLocale.select(requireActivity(), tag);
            }
        });
    }
}
