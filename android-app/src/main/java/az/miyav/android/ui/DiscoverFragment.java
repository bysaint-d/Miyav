package az.miyav.android.ui;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentDiscoverBinding;
import az.miyav.android.databinding.SheetCreateChoiceBinding;
import az.miyav.android.location.DeviceLocation;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.walk.WalkListing;

import java.util.Map;

public class DiscoverFragment extends Fragment {
    private static final String STATE_WALKS = "walk_mode";
    private static final int PAGE = 20;
    private static final int RADIUS_KM = 25;
    private FragmentDiscoverBinding binding;
    private PetCardAdapter adapter;
    private Choice<String> city;
    private Choice<PetSpecies> species;
    private PetQuery query;
    private boolean loading;
    private boolean hasMore;
    private int generation;
    private Double nearLatitude;
    private Double nearLongitude;
    private WalkListingAdapter walks;
    private boolean walkMode;
    private int walkOffset;
    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermission);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false);
        if (savedInstanceState != null) {
            walkMode = savedInstanceState.getBoolean(STATE_WALKS, walkMode);
        }
        adapter = new PetCardAdapter(Ui.app(this), false, this::open);
        walks = new WalkListingAdapter(this::openWalk);
        LinearLayoutManager layout = new LinearLayoutManager(requireContext());
        binding.petList.setLayoutManager(layout);
        binding.petList.setAdapter(walkMode ? walks : adapter);
        binding.petList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                RecyclerView.Adapter<?> shown = recyclerView.getAdapter();
                if (dy > 0 && !loading && hasMore && shown != null && layout.findLastVisibleItemPosition() >= shown.getItemCount() - 4) {
                    if (walkMode) {
                        loadWalks(walkOffset);
                    } else {
                        load(query.nextPage());
                    }
                }
            }
        });
        binding.modeGroup.check(walkMode ? R.id.modeWalks : R.id.modeAdoption);
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked && (checkedId == R.id.modeWalks) != walkMode) {
                walkMode = checkedId == R.id.modeWalks;
                applyMode();
                reload();
            }
        });
        binding.mineChip.setOnClickListener(view -> {
            if (binding.mineChip.isChecked() && !Gate.require(this, ProtectedAction.MANAGE_LISTINGS)) {
                binding.mineChip.setChecked(false);
                return;
            }
            reload();
        });
        applyMode();
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnChildScrollUpCallback((parent, child) -> binding.petList.canScrollVertically(-1));
        binding.refresh.setOnRefreshListener(this::reload);
        city = new Choice<>(binding.cityFilter, Labels.citiesWithAll(),
                value -> value == null ? getString(R.string.filter_all) : value, null);
        species = new Choice<>(binding.speciesFilter, Labels.speciesWithAll(),
                value -> Labels.speciesEmoji(value) + "  " + Labels.species(requireContext(), value), null);
        city.onChange(this::reload);
        species.onChange(this::reload);
        binding.searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                reload();
                return true;
            }
            return false;
        });
        binding.searchLayout.setEndIconOnClickListener(view -> {
            binding.searchInput.setText("");
            reload();
        });
        binding.nearbyChip.setOnClickListener(view -> {
            if (binding.nearbyChip.isChecked()) {
                binding.nearbyChip.setChecked(false);
                locate();
            } else {
                clearNearby();
            }
        });
        binding.notificationsButton.setOnClickListener(view -> {
            if (Gate.require(this, ProtectedAction.NOTIFICATIONS)) {
                Ui.nav(this).navigate(R.id.action_home_to_notifications);
            }
        });
        binding.createButton.setOnClickListener(view -> {
            if (Gate.require(this, ProtectedAction.CREATE_LISTING)) {
                chooseListingKind();
            }
        });
        binding.favoritesButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_favorites));
        binding.messagesButton.setOnClickListener(view -> {
            if (Gate.require(this, ProtectedAction.SEND_MESSAGE)) {
                Ui.nav(this).navigate(R.id.action_global_inbox);
            }
        });
        reload();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUnread();
    }

    /** Red dot on the chat button when someone wrote and the chat was not opened yet. */
    private void refreshUnread() {
        var app = Ui.app(this);
        if (!app.auth().signedIn()) {
            binding.messagesDot.setVisibility(View.GONE);
            return;
        }
        Ui.run(this, () -> app.messaging().unreadCount(app.auth().requireSession()),
                count -> binding.messagesDot.setVisibility(count > 0 ? View.VISIBLE : View.GONE),
                code -> binding.messagesDot.setVisibility(View.GONE));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_WALKS, walkMode);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /** Walk requests and adoption listings are posted from separate forms. */
    private void chooseListingKind() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetCreateChoiceBinding choice = SheetCreateChoiceBinding.inflate(getLayoutInflater());
        choice.walkOption.setOnClickListener(view -> {
            sheet.dismiss();
            Ui.nav(this).navigate(R.id.action_global_walk_editor);
        });
        choice.adoptionOption.setOnClickListener(view -> {
            sheet.dismiss();
            Ui.nav(this).navigate(R.id.action_home_to_editor);
        });
        sheet.setContentView(choice.getRoot());
        sheet.show();
    }

    private void applyMode() {
        binding.petList.setAdapter(walkMode ? walks : adapter);
        binding.searchLayout.setVisibility(walkMode ? View.GONE : View.VISIBLE);
        binding.nearbyChip.setVisibility(walkMode ? View.GONE : View.VISIBLE);
        binding.mineChip.setVisibility(walkMode ? View.VISIBLE : View.GONE);
        binding.cityLayout.setEnabled(walkMode || nearLatitude == null);
        hasMore = false;
    }

    private void openWalk(WalkListing listing) {
        Bundle args = new Bundle();
        args.putString("listingId", listing.id().toString());
        Ui.nav(this).navigate(R.id.action_global_walk_detail, args);
    }

    private void loadWalks(int offset) {
        loading = true;
        int token = ++generation;
        boolean firstPage = offset == 0;
        boolean mine = binding.mineChip.isChecked();
        PetSpecies speciesValue = species.value();
        String cityValue = city.value();
        if (firstPage && walks.isEmpty() && !binding.refresh.isRefreshing()) {
            States.hide(binding.state);
            binding.skeleton.setVisibility(View.VISIBLE);
        }
        Ui.run(this, () -> {
            var app = Ui.app(this);
            return mine
                    ? app.walkListings().mine(app.auth().requireSession())
                    : app.walkListings().board(app.auth().viewer(), speciesValue, cityValue, PAGE, offset);
        }, listings -> {
            if (token != generation) {
                return;
            }
            loading = false;
            walkOffset = offset + listings.size();
            hasMore = !mine && listings.size() == PAGE;
            binding.refresh.setRefreshing(false);
            binding.skeleton.setVisibility(View.GONE);
            walks.showStatus(mine);
            if (firstPage) {
                walks.replace(listings);
                binding.petList.scrollToPosition(0);
            } else {
                walks.append(listings);
            }
            if (walks.isEmpty()) {
                States.message(binding.state, mine ? R.string.wl_mine_empty : R.string.wl_empty, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> {
            if (token != generation) {
                return;
            }
            loading = false;
            binding.refresh.setRefreshing(false);
            binding.skeleton.setVisibility(View.GONE);
            if (walks.isEmpty()) {
                States.message(binding.state, Codes.text(code), this::reload);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void reload() {
        if (walkMode) {
            loadWalks(0);
            return;
        }
        String cityValue = city.value() == null ? "" : city.value();
        load(new PetQuery(species.value(), nearLatitude == null ? cityValue : "", Ui.text(binding.searchInput), PAGE, 0));
    }

    private void load(PetQuery next) {
        loading = true;
        int token = ++generation;
        boolean firstPage = next.offset() == 0;
        Double lat = nearLatitude;
        Double lng = nearLongitude;
        if (firstPage && adapter.isEmpty() && !binding.refresh.isRefreshing()) {
            States.hide(binding.state);
            binding.skeleton.setVisibility(View.VISIBLE);
        }
        Ui.run(this, () -> {
            var app = Ui.app(this);
            var viewer = app.auth().viewer();
            return lat == null
                    ? app.discovery().discover(viewer, next)
                    : app.discovery().near(viewer, lat, lng, RADIUS_KM, next);
        }, pets -> {
            if (token != generation) {
                return;
            }
            loading = false;
            query = next;
            hasMore = pets.size() == next.limit();
            binding.refresh.setRefreshing(false);
            binding.skeleton.setVisibility(View.GONE);
            adapter.origin(lat, lng);
            if (firstPage) {
                adapter.replace(pets);
                binding.petList.scrollToPosition(0);
            } else {
                adapter.append(pets);
            }
            if (adapter.isEmpty()) {
                States.message(binding.state, R.string.home_empty, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> {
            if (token != generation) {
                return;
            }
            loading = false;
            binding.refresh.setRefreshing(false);
            binding.skeleton.setVisibility(View.GONE);
            if (adapter.isEmpty()) {
                States.message(binding.state, Codes.text(code), this::reload);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void open(Pet pet) {
        Bundle args = new Bundle();
        args.putString("petId", pet.id().toString());
        Ui.nav(this).navigate(R.id.action_home_to_detail, args);
    }

    private void locate() {
        if (DeviceLocation.hasAny(requireContext())) {
            fetchLocation();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.location_rationale_title)
                .setMessage(R.string.location_rationale_discover)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> permission.launch(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION}))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onPermission(Map<String, Boolean> result) {
        if (binding == null) {
            return;
        }
        if (DeviceLocation.hasAny(requireContext())) {
            fetchLocation();
            return;
        }
        if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Snackbar.make(binding.getRoot(), R.string.location_denied, Snackbar.LENGTH_LONG)
                    .setAction(R.string.location_settings, view -> startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", requireContext().getPackageName(), null))))
                    .show();
        } else {
            Ui.snack(binding.getRoot(), R.string.location_denied);
        }
    }

    private void fetchLocation() {
        DeviceLocation.current(requireContext(), new DeviceLocation.Callback() {
            @Override
            public void onLocation(Location location) {
                applyLocation(location);
            }

            @Override
            public void onFailure(DeviceLocation.Failure failure) {
                if (binding == null) {
                    return;
                }
                if (failure == DeviceLocation.Failure.DISABLED) {
                    Snackbar.make(binding.getRoot(), R.string.location_disabled, Snackbar.LENGTH_LONG)
                            .setAction(R.string.location_settings, view -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                            .show();
                } else if (failure == DeviceLocation.Failure.PERMISSION) {
                    Ui.snack(binding.getRoot(), R.string.location_denied);
                } else {
                    Ui.snack(binding.getRoot(), R.string.location_unavailable);
                }
            }
        });
    }

    private void applyLocation(Location location) {
        if (binding == null) {
            return;
        }
        if (!ListingLocation.inArea(location.getLatitude(), location.getLongitude())) {
            Ui.snack(binding.getRoot(), R.string.location_out_of_area);
            return;
        }
        nearLatitude = location.getLatitude();
        nearLongitude = location.getLongitude();
        binding.nearbyChip.setChecked(true);
        binding.nearbyChip.setText(getString(R.string.nearby_mode, RADIUS_KM));
        binding.cityLayout.setEnabled(false);
        reload();
    }

    private void clearNearby() {
        nearLatitude = null;
        nearLongitude = null;
        binding.nearbyChip.setChecked(false);
        binding.nearbyChip.setText(R.string.location_use);
        binding.cityLayout.setEnabled(true);
        reload();
    }
}
