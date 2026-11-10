package az.miyav.android.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentWalkBinding;
import az.miyav.android.databinding.SheetWalkerBinding;
import az.miyav.android.location.DeviceLocation;
import az.miyav.android.walk.WalkTracker;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.walk.WalkPet;
import az.miyav.domain.walk.Walker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Gəzinti (NearMe): live map of people walking their pets nearby. */
public class WalkFragment extends Fragment {
    private static final long POLL_MS = 15_000;
    private FragmentWalkBinding binding;
    private WalkerAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<UUID, Marker> markers = new HashMap<>();
    private Marker me;
    private boolean nearbyLoaded;
    private boolean centeredOnce;
    private WalkTracker.State lastEvent;
    private boolean pendingStart;
    private List<UUID> pendingPets = List.of();
    private UUID pendingListing;
    private String pendingMessage = "";
    private String attentionTab = MyWalksFragment.TAB_AGREED;
    private final Runnable poll = this::pollNearby;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderDuration();
            handler.postDelayed(this, 1000);
        }
    };
    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> onStartPermission());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWalkBinding.inflate(inflater, container, false);
        MapSupport.style(binding.map, true);
        binding.map.getController().setZoom(13.0);
        binding.map.getController().setCenter(MapSupport.DEFAULT_CAMERA);
        adapter = new WalkerAdapter(Ui.app(this), this::showWalker);
        binding.nearbyList.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        binding.nearbyList.setAdapter(adapter);
        binding.startButton.setOnClickListener(view -> WalkStartSheet.open(getChildFragmentManager()));
        getChildFragmentManager().setFragmentResultListener(WalkStartSheet.RESULT, getViewLifecycleOwner(), (key, result) -> {
            List<UUID> pets = new ArrayList<>();
            List<String> ids = result.getStringArrayList(WalkStartSheet.PETS);
            for (String id : ids == null ? List.<String>of() : ids) {
                pets.add(UUID.fromString(id));
            }
            String listing = result.getString(WalkStartSheet.LISTING, "");
            pendingPets = pets;
            pendingListing = listing.isEmpty() ? null : UUID.fromString(listing);
            pendingMessage = result.getString(WalkStartSheet.MESSAGE, "");
            pendingStart = true;
            requestStartPermissions();
        });
        binding.pauseButton.setOnClickListener(view -> togglePause());
        binding.endButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.walk_end_confirm)
                .setPositiveButton(R.string.walk_end, (dialog, which) -> tracker().end(this::onError))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        binding.locateButton.setOnClickListener(view -> locateMe(true));
        binding.historyButton.setOnClickListener(view -> {
            if (Gate.require(this, az.miyav.domain.auth.ProtectedAction.WALK)) {
                Ui.nav(this).navigate(R.id.action_global_walk_history);
            }
        });
        binding.myWalksButton.setOnClickListener(view -> {
            if (Gate.require(this, az.miyav.domain.auth.ProtectedAction.WALK)) {
                MyWalksFragment.open(this, attentionTab);
            }
        });
        tracker().state().observe(getViewLifecycleOwner(), this::render);
        tracker().sync(this::onError);
        if (DeviceLocation.hasAny(requireContext())) {
            locateMe(false);
        }
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.map.onResume();
        handler.post(tick);
        handler.post(poll);
        loadAttention();
    }

    /** "My walks · 2": offers to answer and upcoming agreed walks, so nothing waits unseen. */
    private void loadAttention() {
        if (!Ui.app(this).auth().signedIn()) {
            return;
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.walkListings().myWalks(app.auth().requireSession());
        }, walks -> {
            Instant now = Instant.now();
            int count = walks.attention(now);
            boolean review = walks.requests(now).stream()
                    .anyMatch(listing -> walks.stage(listing, now) == az.miyav.domain.walk.MyWalks.Stage.OFFERS_TO_REVIEW);
            attentionTab = review ? MyWalksFragment.TAB_REQUESTS : MyWalksFragment.TAB_AGREED;
            binding.myWalksButton.setText(count == 0 ? getString(R.string.my_walks_title)
                    : getString(R.string.my_walks_title) + "  ·  " + count);
        }, code -> {
        });
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(poll);
        binding.map.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        binding.map.onDetach();
        markers.clear();
        me = null;
        binding = null;
        super.onDestroyView();
    }

    private WalkTracker tracker() {
        return Ui.app(this).walkTracker();
    }

    private void render(WalkTracker.State state) {
        if (binding == null) {
            return;
        }
        boolean open = state.session != null;
        binding.idlePanel.setVisibility(open ? View.GONE : View.VISIBLE);
        binding.activePanel.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            boolean paused = state.paused();
            binding.statusText.setText(paused ? R.string.walk_paused : R.string.walk_active);
            binding.statusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), paused ? R.color.brand_sun : R.color.brand_mint)));
            binding.pauseButton.setText(paused ? R.string.walk_resume : R.string.walk_pause);
            binding.pauseButton.setIconResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
            binding.pauseButton.setEnabled(true);
            binding.endButton.setEnabled(true);
            String pets = state.session.petName();
            binding.petsText.setVisibility(pets == null || pets.isBlank() ? View.GONE : View.VISIBLE);
            binding.petsText.setText(getString(R.string.walk_with, pets));
        }
        if (!state.walking()) {
            clearWalkers();
            binding.headerStatus.setText(state.paused() ? R.string.walk_paused : R.string.walk_subtitle);
        }
        if (state.latitude != null && state.longitude != null) {
            showMe(state.latitude, state.longitude, state.session != null ? state.session.species() : null);
        }
        if (state.event != null && state != lastEvent) {
            lastEvent = state;
            switch (state.event) {
                case "walk.started" -> Ui.snack(binding.getRoot(), R.string.walk_started);
                case "walk.ended" -> Snackbar.make(binding.getRoot(), R.string.walk_ended, Snackbar.LENGTH_LONG)
                        .setAction(R.string.walk_history_see, view -> Ui.nav(this).navigate(R.id.action_global_walk_history))
                        .show();
                case "walk.no_session" -> Ui.snack(binding.getRoot(), R.string.walk_no_session);
                default -> {
                }
            }
        }
        renderDuration();
        if (state.walking() && !nearbyLoaded) {
            handler.removeCallbacks(poll);
            handler.post(poll);
        }
    }

    private void renderDuration() {
        if (binding == null) {
            return;
        }
        WalkTracker.State state = tracker().snapshot();
        if (state.session == null) {
            binding.durationText.setText("");
            binding.distanceText.setText("");
            return;
        }
        long seconds = Math.max(0, Duration.between(state.session.startedAt(), Instant.now()).getSeconds());
        binding.durationText.setText(String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60));
        binding.distanceText.setText(getString(R.string.walk_live_distance,
                WalkHistoryFragment.km(requireContext(), tracker().distanceMeters())));
    }

    private void pollNearby() {
        handler.removeCallbacks(poll);
        if (binding == null) {
            return;
        }
        if (!tracker().snapshot().walking()) {
            return;
        }
        if (!nearbyLoaded) {
            binding.nearbySkeleton.setVisibility(View.VISIBLE);
            binding.nearbyEmpty.setVisibility(View.GONE);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.walkingService().nearby(app.auth().requireSession());
        }, this::showWalkers, code -> {
            binding.nearbySkeleton.setVisibility(View.GONE);
            if (!"network.unavailable".equals(code)) {
                Ui.snack(binding.getRoot(), code);
            }
        });
        handler.postDelayed(poll, POLL_MS);
    }

    private void showWalkers(List<Walker> walkers) {
        if (!tracker().snapshot().walking()) {
            clearWalkers();
            return;
        }
        nearbyLoaded = true;
        binding.nearbySkeleton.setVisibility(View.GONE);
        adapter.replace(walkers);
        binding.nearbyList.setVisibility(walkers.isEmpty() ? View.GONE : View.VISIBLE);
        binding.nearbyEmpty.setVisibility(walkers.isEmpty() ? View.VISIBLE : View.GONE);
        binding.nearbyEmpty.setText(R.string.walk_nearby_empty);
        binding.headerStatus.setText(getString(R.string.walk_nearby_count, walkers.size()));
        Set<UUID> seen = new HashSet<>();
        for (Walker walker : walkers) {
            seen.add(walker.sessionId());
            Marker marker = markers.get(walker.sessionId());
            GeoPoint point = new GeoPoint(walker.latitude(), walker.longitude());
            if (marker == null) {
                marker = new Marker(binding.map);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                marker.setIcon(MapSupport.emojiMarker(requireContext(), Labels.speciesEmoji(walker.species()),
                        Labels.speciesColor(requireContext(), walker.species()), false));
                marker.setInfoWindow(null);
                binding.map.getOverlays().add(marker);
                markers.put(walker.sessionId(), marker);
                Marker target = marker;
                Ui.app(this).photos().avatarBitmap(walker.avatarPath(), bitmap -> {
                    if (binding != null) {
                        target.setIcon(MapSupport.avatarMarker(requireContext(), bitmap));
                        binding.map.invalidate();
                    }
                });
            }
            marker.setPosition(point);
            marker.setTitle(walker.fullName());
            marker.setOnMarkerClickListener((clicked, map) -> {
                showWalker(walker);
                return true;
            });
        }
        markers.entrySet().removeIf(entry -> {
            if (!seen.contains(entry.getKey())) {
                binding.map.getOverlays().remove(entry.getValue());
                return true;
            }
            return false;
        });
        binding.map.invalidate();
    }

    private void clearWalkers() {
        nearbyLoaded = false;
        if (binding == null) {
            return;
        }
        for (Marker marker : markers.values()) {
            binding.map.getOverlays().remove(marker);
        }
        markers.clear();
        adapter.replace(List.of());
        binding.nearbyList.setVisibility(View.GONE);
        binding.nearbySkeleton.setVisibility(View.GONE);
        binding.nearbyEmpty.setVisibility(View.GONE);
        binding.map.invalidate();
    }

    private void showMe(double latitude, double longitude, PetSpecies species) {
        GeoPoint point = new GeoPoint(latitude, longitude);
        if (me == null) {
            me = new Marker(binding.map);
            me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            me.setInfoWindow(null);
            binding.map.getOverlays().add(me);
        }
        me.setIcon(MapSupport.emojiMarker(requireContext(), Labels.speciesEmoji(species),
                ContextCompat.getColor(requireContext(), R.color.pastel_peach), true));
        me.setPosition(point);
        if (!centeredOnce) {
            centeredOnce = true;
            binding.map.getController().setZoom(15.5);
            binding.map.getController().setCenter(point);
        } else if (!binding.map.getBoundingBox().increaseByScale(0.7f).contains(point)) {
            binding.map.getController().animateTo(point);
        }
        binding.map.invalidate();
    }

    private void locateMe(boolean animate) {
        WalkTracker.State state = tracker().snapshot();
        if (state.latitude != null && animate) {
            binding.map.getController().animateTo(new GeoPoint(state.latitude, state.longitude));
            return;
        }
        if (!DeviceLocation.hasAny(requireContext())) {
            if (animate) {
                Ui.snack(binding.getRoot(), R.string.walk_permission_needed);
            }
            return;
        }
        DeviceLocation.current(requireContext(), new DeviceLocation.Callback() {
            @Override
            public void onLocation(Location location) {
                if (binding == null) {
                    return;
                }
                showMe(location.getLatitude(), location.getLongitude(), null);
                if (animate) {
                    binding.map.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));
                }
            }

            @Override
            public void onFailure(DeviceLocation.Failure failure) {
                if (binding != null && animate) {
                    Ui.snack(binding.getRoot(), failure == DeviceLocation.Failure.DISABLED
                            ? R.string.location_disabled : R.string.location_unavailable);
                }
            }
        });
    }

    private void requestStartPermissions() {
        boolean needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        if (DeviceLocation.hasFine(requireContext()) && !needsNotifications) {
            onStartPermission();
            return;
        }
        String[] wanted = needsNotifications
                ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        permission.launch(wanted);
    }

    private void onStartPermission() {
        if (binding == null || !pendingStart) {
            return;
        }
        if (!DeviceLocation.hasAny(requireContext())) {
            Snackbar.make(binding.getRoot(), R.string.walk_permission_needed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.location_settings, view -> startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", requireContext().getPackageName(), null))))
                    .show();
            return;
        }
        if (!DeviceLocation.hasFine(requireContext())) {
            Ui.snack(binding.getRoot(), R.string.walk_approximate_only);
        }
        binding.startButton.setEnabled(false);
        binding.headerStatus.setText(R.string.walk_waiting_fix);
        pendingStart = false;
        List<UUID> pets = pendingPets;
        UUID listing = pendingListing;
        String message = pendingMessage;
        DeviceLocation.current(requireContext(), new DeviceLocation.Callback() {
            @Override
            public void onLocation(Location location) {
                if (binding == null) {
                    return;
                }
                binding.startButton.setEnabled(true);
                if (!ListingLocation.inArea(location.getLatitude(), location.getLongitude())) {
                    binding.headerStatus.setText(R.string.walk_subtitle);
                    Ui.snack(binding.getRoot(), R.string.location_out_of_area);
                    return;
                }
                tracker().startWith(pets, listing, message, location, WalkFragment.this::onError);
            }

            @Override
            public void onFailure(DeviceLocation.Failure failure) {
                if (binding == null) {
                    return;
                }
                binding.startButton.setEnabled(true);
                binding.headerStatus.setText(R.string.walk_subtitle);
                Ui.snack(binding.getRoot(), failure == DeviceLocation.Failure.DISABLED
                        ? R.string.location_disabled : R.string.location_unavailable);
            }
        });
    }

    private void togglePause() {
        WalkTracker.State state = tracker().snapshot();
        if (state.walking()) {
            tracker().pause(this::onError);
            return;
        }
        if (!DeviceLocation.hasAny(requireContext())) {
            requestStartPermissions();
            return;
        }
        binding.pauseButton.setEnabled(false);
        binding.endButton.setEnabled(false);
        binding.statusText.setText(R.string.walk_waiting_fix);
        DeviceLocation.current(requireContext(), new DeviceLocation.Callback() {
            @Override
            public void onLocation(Location location) {
                tracker().resume(location, WalkFragment.this::onError);
            }

            @Override
            public void onFailure(DeviceLocation.Failure failure) {
                if (binding != null) {
                    render(tracker().snapshot());
                    Ui.snack(binding.getRoot(), failure == DeviceLocation.Failure.DISABLED
                            ? R.string.location_disabled : R.string.location_unavailable);
                }
            }
        });
    }

    private void showWalker(Walker walker) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetWalkerBinding view = SheetWalkerBinding.inflate(getLayoutInflater());
        view.nameView.setText(walker.fullName());
        view.petView.setText(WalkerAdapter.petLine(requireContext(), walker));
        long minutes = walker.startedAt() == null ? 0 : Math.max(1, Duration.between(walker.startedAt(), Instant.now()).toMinutes());
        view.distanceView.setText(getString(R.string.format_pair,
                getString(R.string.walk_distance, Labels.distance(requireContext(), walker.distanceMeters())),
                getString(R.string.walk_since, minutes)));
        view.messageView.setVisibility(walker.message().isEmpty() ? View.GONE : View.VISIBLE);
        view.messageView.setText(walker.message());
        view.bioView.setVisibility(walker.bio().isEmpty() ? View.GONE : View.VISIBLE);
        view.bioView.setText(walker.bio());
        Ui.app(this).photos().avatar(view.avatarView, walker.avatarPath());
        PetTileAdapter pets = new PetTileAdapter(Ui.app(this), null, null);
        view.petList.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        view.petList.setAdapter(pets);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.walkingService().pets(app.auth().requireSession(), walker.sessionId());
        }, found -> {
            List<PetTileAdapter.Tile> tiles = new ArrayList<>();
            for (WalkPet pet : found) {
                tiles.add(new PetTileAdapter.Tile(pet.name(), pet.name(),
                        Labels.petMeta(requireContext(), pet.species(), pet.breed(), pet.ageYears().orElse(null)),
                        pet.species(), pet.photoPath().orElse(null), pet.fromJob()));
            }
            pets.replace(tiles);
            view.petsTitle.setVisibility(tiles.isEmpty() ? View.GONE : View.VISIBLE);
            view.petList.setVisibility(tiles.isEmpty() ? View.GONE : View.VISIBLE);
        }, code -> {
        });
        view.profileButton.setOnClickListener(button -> {
            sheet.dismiss();
            Bundle args = new Bundle();
            args.putString("userId", walker.userId().toString());
            Ui.nav(this).navigate(R.id.action_global_public_profile, args);
        });
        view.whatsappButton.setVisibility(walker.contactDigits().isPresent() ? View.VISIBLE : View.GONE);
        walker.contactDigits().ifPresent(digits -> view.whatsappButton.setOnClickListener(button ->
                Contact.whatsApp(requireContext(), digits, getString(R.string.contact_walk_message))));
        sheet.setContentView(view.getRoot());
        sheet.show();
    }

    private void onError(String code) {
        handler.post(() -> {
            if (binding != null) {
                binding.startButton.setEnabled(true);
                render(tracker().snapshot());
                if ("auth.session_expired".equals(code)) {
                    Ui.signedOut(this, true);
                } else {
                    Ui.snack(binding.getRoot(), code);
                }
            }
        });
    }
}
