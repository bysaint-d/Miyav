package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentHomeBinding;

import java.util.UUID;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        if (Ui.app(this).authService().current().isEmpty()) {
            binding.getRoot().post(() -> {
                if (isAdded()) {
                    Ui.signedOut(this, false);
                }
            });
            return binding.getRoot();
        }
        binding.bottomNav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            highlightWalk(item.getItemId() == R.id.tab_walk);
            return true;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> {
        });
        binding.walkButton.setOnClickListener(view -> {
            view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).withEndAction(() ->
                    view.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator()).setDuration(220).start()).start();
            binding.bottomNav.setSelectedItemId(R.id.tab_walk);
        });
        if (getChildFragmentManager().findFragmentById(R.id.tabHost) == null) {
            show(binding.bottomNav.getSelectedItemId());
        }
        highlightWalk(binding.bottomNav.getSelectedItemId() == R.id.tab_walk);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        UUID pending = Ui.app(this).takePendingListing();
        if (pending != null && binding != null) {
            Bundle args = new Bundle();
            args.putString("petId", pending.toString());
            Ui.nav(this).navigate(R.id.action_home_to_detail, args);
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void highlightWalk(boolean selected) {
        float scale = selected ? 1.08f : 1f;
        binding.walkButton.animate().scaleX(scale).scaleY(scale).setDuration(200).start();
        binding.walkButton.setElevation(getResources().getDimension(R.dimen.elevation_fab) * (selected ? 1.4f : 1f));
    }

    private void show(int itemId) {
        Fragment tab;
        if (itemId == R.id.tab_pets) {
            tab = new MyPetsFragment();
        } else if (itemId == R.id.tab_walk) {
            tab = new WalkFragment();
        } else if (itemId == R.id.tab_applications) {
            tab = new ApplicationsFragment();
        } else if (itemId == R.id.tab_profile) {
            tab = new ProfileFragment();
        } else {
            tab = new DiscoverFragment();
        }
        getChildFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.tabHost, tab)
                .commit();
    }
}
