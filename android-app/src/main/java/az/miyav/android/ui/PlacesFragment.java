package az.miyav.android.ui;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentPlacesBinding;
import az.miyav.android.databinding.ItemPlaceBinding;
import az.miyav.android.location.DeviceLocation;
import az.miyav.application.PlacesService;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.place.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Nearest vets and pet shops; each opens turn-by-turn directions in Google Maps. Works without an account. */
public class PlacesFragment extends Fragment {
    private static final String STATE_SHOPS = "places_shops";
    private FragmentPlacesBinding binding;
    private final Places adapter = new Places();
    private Place.Kind kind = Place.Kind.VET;
    private double latitude = PlacesService.DEFAULT_LATITUDE;
    private double longitude = PlacesService.DEFAULT_LONGITUDE;
    private boolean located;
    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermission);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPlacesBinding.inflate(inflater, container, false);
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_SHOPS)) {
            kind = Place.Kind.PET_SHOP;
        }
        binding.kindGroup.check(kind == Place.Kind.VET ? R.id.kindVets : R.id.kindShops);
        binding.kindGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                kind = checkedId == R.id.kindShops ? Place.Kind.PET_SHOP : Place.Kind.VET;
                load();
            }
        });
        binding.placeList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.placeList.setAdapter(adapter);
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnChildScrollUpCallback((parent, child) -> binding.placeList.canScrollVertically(-1));
        binding.refresh.setOnRefreshListener(() -> {
            Ui.app(this).places().refresh();
            load();
        });
        binding.locationChip.setOnClickListener(view -> locate(true));
        renderLocation();
        load();
        locate(false);
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOPS, kind == Place.Kind.PET_SHOP);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (adapter.getItemCount() == 0 && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Place.Kind wanted = kind;
        double lat = latitude;
        double lng = longitude;
        Ui.run(this, () -> Ui.app(this).places().nearest(wanted, lat, lng), nearby -> {
            binding.refresh.setRefreshing(false);
            if (wanted != kind) {
                return;
            }
            adapter.replace(nearby);
            binding.placeList.scrollToPosition(0);
            if (nearby.isEmpty()) {
                States.message(binding.state, R.string.places_empty, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> {
            binding.refresh.setRefreshing(false);
            States.message(binding.state, Codes.text(code), this::load);
        });
    }

    /** Uses the device location when allowed; {@code ask} shows the permission prompt. */
    private void locate(boolean ask) {
        Context context = requireContext();
        if (DeviceLocation.hasAny(context)) {
            DeviceLocation.current(context, new DeviceLocation.Callback() {
                @Override
                public void onLocation(Location location) {
                    if (binding == null || !ListingLocation.inArea(location.getLatitude(), location.getLongitude())) {
                        return;
                    }
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    located = true;
                    renderLocation();
                    load();
                }

                @Override
                public void onFailure(DeviceLocation.Failure failure) {
                    if (binding != null && ask) {
                        Ui.snack(binding.getRoot(), failure == DeviceLocation.Failure.DISABLED
                                ? R.string.location_disabled : R.string.location_unavailable);
                    }
                }
            });
        } else if (ask) {
            permission.launch(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void onPermission(Map<String, Boolean> result) {
        if (binding == null) {
            return;
        }
        if (DeviceLocation.hasAny(requireContext())) {
            locate(false);
        } else {
            Ui.snack(binding.getRoot(), R.string.location_denied);
        }
    }

    private void renderLocation() {
        binding.locationChip.setText(located ? R.string.places_location_mine : R.string.places_location_default);
    }

    private final class Places extends RecyclerView.Adapter<Places.Holder> {
        private final List<PlacesService.Nearby> rows = new ArrayList<>();

        void replace(List<PlacesService.Nearby> next) {
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemPlaceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Context context = holder.itemView.getContext();
            PlacesService.Nearby nearby = rows.get(position);
            Place place = nearby.place();
            holder.binding.iconView.setImageResource(place.kind() == Place.Kind.VET ? R.drawable.ic_vet : R.drawable.ic_paw);
            holder.binding.nameView.setText(place.name());
            List<String> details = new ArrayList<>();
            place.address().ifPresent(details::add);
            place.openingHours().ifPresent(hours -> details.add(context.getString(R.string.places_hours, hours)));
            holder.binding.detailView.setText(String.join(" · ", details));
            holder.binding.detailView.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
            holder.binding.distanceView.setText(Labels.distance(context, nearby.meters()));
            holder.binding.directionsButton.setOnClickListener(view ->
                    Contact.directions(context, place.latitude(), place.longitude()));
            holder.binding.callButton.setVisibility(place.phone().isPresent() ? View.VISIBLE : View.GONE);
            place.phone().ifPresent(phone -> holder.binding.callButton.setOnClickListener(view -> Contact.dial(context, phone)));
            holder.itemView.setOnClickListener(view -> Contact.directions(context, place.latitude(), place.longitude()));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemPlaceBinding binding;

            Holder(ItemPlaceBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
