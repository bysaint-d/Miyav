package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentHomeBinding;
import az.miyav.domain.auth.AuthState;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;

import java.util.Optional;
import java.util.UUID;

/**
 * Home with the bottom navigation. Guests see the full Discover experience; account-only tabs show a
 * gentle sign-in card instead of a wall. After sign-in, the tab re-renders and any pending action resumes.
 */
public class HomeFragment extends Fragment {
    private static final String STATE_TAB = "selected_tab";
    private FragmentHomeBinding binding;
    private Boolean renderedSignedIn;
    private int selectedTab = R.id.tab_listings;
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    az.miyav.android.sync.NotificationPollWorker.schedule(requireContext());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        // The fragment owns the selected tab: the bar's own state restore runs after onCreateView and
        // would otherwise highlight a different tab than the one shown (for example after a language change).
        binding.bottomNav.setSaveEnabled(false);
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_TAB, R.id.tab_listings);
        }
        binding.bottomNav.setSelectedItemId(selectedTab);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            selectedTab = item.getItemId();
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
        boolean signedIn = Ui.app(this).auth().signedIn();
        Fragment current = getChildFragmentManager().findFragmentById(R.id.tabHost);
        if (fits(current, binding.bottomNav.getSelectedItemId(), signedIn)) {
            renderedSignedIn = signedIn;
        } else {
            show(binding.bottomNav.getSelectedItemId());
        }
        highlightWalk(binding.bottomNav.getSelectedItemId() == R.id.tab_walk);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.app(this).authState().observe(getViewLifecycleOwner(), this::onAuthState);
    }

    @Override
    public void onResume() {
        super.onResume();
        UUID pending = Ui.app(this).takePendingListing();
        if (pending != null && binding != null) {
            openListing(pending.toString());
        }
        String tap = Ui.app(this).takePendingTap();
        if (tap != null && binding != null && Ui.app(this).auth().signedIn()) {
            if (tap.isEmpty()) {
                Ui.nav(this).navigate(R.id.action_home_to_notifications);
            } else {
                Ui.openChat(this, tap, null, null, null);
            }
        }
        askForNotifications();
    }

    /** Android 13+ needs consent before phone notifications; asked once, after sign-in. */
    private void askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU || !Ui.app(this).auth().signedIn()) {
            return;
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("miyav_push", android.content.Context.MODE_PRIVATE);
        if (prefs.getBoolean("asked", false)) {
            return;
        }
        prefs.edit().putBoolean("asked", true).apply();
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void onAuthState(AuthState state) {
        boolean signedIn = Ui.app(this).auth().signedIn();
        if (renderedSignedIn == null || renderedSignedIn != signedIn) {
            Fragment current = getChildFragmentManager().findFragmentById(R.id.tabHost);
            if (fits(current, binding.bottomNav.getSelectedItemId(), signedIn)) {
                renderedSignedIn = signedIn;
            } else {
                show(binding.bottomNav.getSelectedItemId());
            }
        }
        if (signedIn) {
            resumePending();
        }
    }

    /** Returns the user to the action they tried as a guest. Listing actions are finished by the detail screen. */
    private void resumePending() {
        MiyavApplication app = Ui.app(this);
        Optional<PendingAction> stored = app.auth().pending();
        if (stored.isEmpty() || Ui.nav(this).getCurrentDestination() == null
                || Ui.nav(this).getCurrentDestination().getId() != R.id.homeFragment) {
            return;
        }
        PendingAction action = stored.get();
        if (action.action() == ProtectedAction.APPLY_ADOPTION || action.action() == ProtectedAction.CONTACT_OWNER) {
            if (!action.argument("petId").isEmpty()) {
                openListing(action.argument("petId"));
                return;
            }
        }
        if (action.action() == ProtectedAction.OFFER_WALK && !action.argument("listingId").isEmpty()) {
            Bundle args = new Bundle();
            args.putString("listingId", action.argument("listingId"));
            Ui.nav(this).navigate(R.id.action_global_walk_detail, args);
            return;
        }
        app.auth().takePending();
        switch (action.action()) {
            case CREATE_LISTING -> Ui.nav(this).navigate(R.id.action_home_to_editor);
            case MANAGE_LISTINGS -> binding.bottomNav.setSelectedItemId(R.id.tab_profile);
            case APPLICATIONS -> binding.bottomNav.setSelectedItemId(R.id.tab_applications);
            case WALK -> binding.bottomNav.setSelectedItemId(R.id.tab_walk);
            case EDIT_PROFILE -> binding.bottomNav.setSelectedItemId(R.id.tab_profile);
            case CHANGE_PASSWORD -> Ui.nav(this).navigate(R.id.action_home_to_password);
            case NOTIFICATIONS -> Ui.nav(this).navigate(R.id.action_home_to_notifications);
            case SYNC_FAVORITES -> Ui.nav(this).navigate(R.id.action_global_favorites);
            default -> {
            }
        }
    }

    private void openListing(String petId) {
        Bundle args = new Bundle();
        args.putString("petId", petId);
        Ui.nav(this).navigate(R.id.action_home_to_detail, args);
    }

    private void highlightWalk(boolean selected) {
        float scale = selected ? 1.08f : 1f;
        binding.walkButton.animate().scaleX(scale).scaleY(scale).setDuration(200).start();
        binding.walkButton.setElevation(getResources().getDimension(R.dimen.elevation_fab) * (selected ? 1.4f : 1f));
    }

    private static boolean fits(Fragment current, int itemId, boolean signedIn) {
        if (current == null) {
            return false;
        }
        if (itemId == R.id.tab_listings) {
            return current instanceof DiscoverFragment;
        }
        if (itemId == R.id.tab_vets) {
            return current instanceof PlacesFragment;
        }
        return signedIn != (current instanceof GuestTab);
    }

    private void show(int itemId) {
        boolean signedIn = Ui.app(this).auth().signedIn();
        renderedSignedIn = signedIn;
        Fragment tab;
        if (itemId == R.id.tab_vets) {
            tab = new PlacesFragment();
        } else if (itemId == R.id.tab_walk) {
            tab = signedIn ? new WalkFragment() : GuestGateFragment.of(ProtectedAction.WALK);
        } else if (itemId == R.id.tab_applications) {
            tab = signedIn ? new ApplicationsFragment() : GuestGateFragment.of(ProtectedAction.APPLICATIONS);
        } else if (itemId == R.id.tab_profile) {
            tab = signedIn ? new ProfileFragment() : new GuestProfileFragment();
        } else {
            tab = new DiscoverFragment();
        }
        getChildFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.tabHost, tab)
                .commit();
    }

    /** Marker for tabs that render the guest variant, so a sign-in swaps them for the real screen. */
    interface GuestTab {
    }
}
