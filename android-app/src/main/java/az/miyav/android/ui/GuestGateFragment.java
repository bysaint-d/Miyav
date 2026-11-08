package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentGuestGateBinding;
import az.miyav.domain.auth.ProtectedAction;

/** Guest version of an account-only tab: explains the feature and offers sign-in, without blocking the app. */
public class GuestGateFragment extends Fragment implements HomeFragment.GuestTab {
    private static final String ARG_ACTION = "action";

    static GuestGateFragment of(ProtectedAction action) {
        GuestGateFragment fragment = new GuestGateFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACTION, action.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentGuestGateBinding binding = FragmentGuestGateBinding.inflate(inflater, container, false);
        ProtectedAction action = ProtectedAction.fromName(requireArguments().getString(ARG_ACTION));
        binding.titleView.setText(switch (action) {
            case WALK -> R.string.guest_gate_walk_title;
            case APPLICATIONS -> R.string.guest_gate_applications_title;
            default -> R.string.guest_gate_pets_title;
        });
        binding.iconView.setImageResource(switch (action) {
            case WALK -> R.drawable.ic_walk;
            case APPLICATIONS -> R.drawable.ic_document;
            default -> R.drawable.ic_paw;
        });
        binding.bodyView.setText(Gate.prompt(action));
        binding.signInButton.setOnClickListener(view -> Gate.require(this, action));
        binding.discoverButton.setOnClickListener(view -> {
            BottomNavigationView nav = requireParentFragment().requireView().findViewById(R.id.bottomNav);
            nav.setSelectedItemId(R.id.tab_discover);
        });
        return binding.getRoot();
    }
}
