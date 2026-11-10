package az.miyav.android.ui;

import android.Manifest;
import android.content.Context;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
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

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentPlacesBinding;
import az.miyav.android.databinding.ItemPlaceBinding;
import az.miyav.android.databinding.ItemPlaceSponsoredBinding;
import az.miyav.android.location.DeviceLocation;
import az.miyav.application.PlacesService;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.place.Place;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vets and pet shops near the member, as a list or on a map, with "open now" and 24/7 filters. Every place
 * offers directions, and a call or WhatsApp when the number is known. Works without an account.
 */
public class PlacesFragment extends Fragment {
    private static final String STATE_SHOPS = "places_shops";
    private static final String STATE_MAP = "places_map";
    private static final String STATE_FILTER = "places_filter";
    private static final int CAMERA_PLACES = 3;
    private FragmentPlacesBinding binding;
    private final Places adapter = new Places();
    private final Map<UUID, Marker> markers = new HashMap<>();
    private Place.Kind kind = Place.Kind.VET;
    private PlacesService.Filter filter = PlacesService.Filter.ALL;
    private boolean mapMode;
    private boolean mapReady;
    private boolean cameraPlaced;
    private double latitude = PlacesService.DEFAULT_LATITUDE;
    private double longitude = PlacesService.DEFAULT_LONGITUDE;
    private boolean located;
    private Marker me;
    private UUID selected;
    private List<PlacesService.Nearby> shown = List.of();
    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermission);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPlacesBinding.inflate(inflater, container, false);
        if (savedInstanceState != null) {
            kind = savedInstanceState.getBoolean(STATE_SHOPS) ? Place.Kind.PET_SHOP : Place.Kind.VET;
            mapMode = savedInstanceState.getBoolean(STATE_MAP);
            filter = PlacesService.Filter.values()[savedInstanceState.getInt(STATE_FILTER, 0)];
        }
        binding.kindGroup.check(kind == Place.Kind.VET ? R.id.kindVets : R.id.kindShops);
        binding.kindGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                kind = checkedId == R.id.kindShops ? Place.Kind.PET_SHOP : Place.Kind.VET;
                selected = null;
                cameraPlaced = false;
                load();
            }
        });
        binding.viewGroup.check(mapMode ? R.id.viewMap : R.id.viewList);
        binding.viewGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                mapMode = checkedId == R.id.viewMap;
                applyMode();
            }
        });
        binding.openNowChip.setChecked(filter == PlacesService.Filter.OPEN_NOW);
        binding.allDayChip.setChecked(filter == PlacesService.Filter.ALWAYS_OPEN);
        binding.openNowChip.setOnClickListener(view -> pickFilter(binding.openNowChip.isChecked() ? PlacesService.Filter.OPEN_NOW : PlacesService.Filter.ALL));
        binding.allDayChip.setOnClickListener(view -> pickFilter(binding.allDayChip.isChecked() ? PlacesService.Filter.ALWAYS_OPEN : PlacesService.Filter.ALL));
        binding.placeList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.placeList.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(adapter, new Attribution()));
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnChildScrollUpCallback((parent, child) -> binding.placeList.canScrollVertically(-1));
        binding.refresh.setOnRefreshListener(() -> {
            Ui.app(this).places().refresh();
            load();
        });
        binding.locationChip.setOnClickListener(view -> locate(true));
        binding.myLocationButton.setOnClickListener(view -> {
            if (located) {
                binding.map.getController().animateTo(new GeoPoint(latitude, longitude), 15.0, 600L);
            } else {
                locate(true);
            }
        });
        renderLocation();
        applyMode();
        load();
        locate(false);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapReady) {
            binding.map.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapReady) {
            binding.map.onPause();
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOPS, kind == Place.Kind.PET_SHOP);
        outState.putBoolean(STATE_MAP, mapMode);
        outState.putInt(STATE_FILTER, filter.ordinal());
    }

    @Override
    public void onDestroyView() {
        if (mapReady) {
            binding.map.onDetach();
        }
        markers.clear();
        me = null;
        mapReady = false;
        binding = null;
        super.onDestroyView();
    }

    private void pickFilter(PlacesService.Filter next) {
        filter = next;
        binding.openNowChip.setChecked(filter == PlacesService.Filter.OPEN_NOW);
        binding.allDayChip.setChecked(filter == PlacesService.Filter.ALWAYS_OPEN);
        selected = null;
        load();
    }

    private void applyMode() {
        binding.refresh.setVisibility(mapMode ? View.GONE : View.VISIBLE);
        binding.mapPane.setVisibility(mapMode ? View.VISIBLE : View.GONE);
        if (mapMode) {
            prepareMap();
            renderMap();
        }
    }

    private void prepareMap() {
        if (mapReady) {
            return;
        }
        MapSupport.style(binding.map, true);
        binding.map.getController().setZoom(12.5);
        binding.map.getController().setCenter(new GeoPoint(latitude, longitude));
        binding.map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                select(null);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        }));
        mapReady = true;
        binding.map.onResume();
    }

    private void load() {
        if (adapter.getItemCount() == 0 && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Place.Kind wanted = kind;
        PlacesService.Filter wantedFilter = filter;
        double lat = latitude;
        double lng = longitude;
        Ui.run(this, () -> Ui.app(this).places().nearest(wanted, lat, lng, wantedFilter), nearby -> {
            binding.refresh.setRefreshing(false);
            if (wanted != kind || wantedFilter != filter) {
                return;
            }
            shown = nearby;
            adapter.replace(nearby);
            binding.placeList.scrollToPosition(0);
            binding.summaryView.setText(getResources().getQuantityString(
                    kind == Place.Kind.VET ? R.plurals.places_count_vets : R.plurals.places_count_shops, nearby.size(), nearby.size()));
            int empty = filter == PlacesService.Filter.ALL ? R.string.places_empty : R.string.places_empty_filtered;
            if (nearby.isEmpty()) {
                States.message(binding.state, empty, null);
            } else {
                States.hide(binding.state);
            }
            binding.mapEmpty.setText(empty);
            binding.mapEmpty.setVisibility(nearby.isEmpty() ? View.VISIBLE : View.GONE);
            renderMap();
        }, code -> {
            binding.refresh.setRefreshing(false);
            States.message(binding.state, Codes.text(code), this::load);
        });
    }

    private void renderMap() {
        if (!mapReady || binding == null) {
            return;
        }
        Context context = requireContext();
        for (Marker marker : markers.values()) {
            binding.map.getOverlays().remove(marker);
        }
        markers.clear();
        List<PlacesService.Nearby> drawOrder = new ArrayList<>(shown);
        drawOrder.sort((left, right) -> Boolean.compare(left.place().sponsored(), right.place().sponsored()));
        for (PlacesService.Nearby nearby : drawOrder) {
            Place place = nearby.place();
            Marker marker = new Marker(binding.map);
            marker.setPosition(new GeoPoint(place.latitude(), place.longitude()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setIcon(icon(context, place, place.id().equals(selected)));
            marker.setInfoWindow(null);
            marker.setOnMarkerClickListener((clicked, map) -> {
                select(place.id());
                return true;
            });
            binding.map.getOverlays().add(marker);
            markers.put(place.id(), marker);
        }
        renderMe();
        placeCamera();
        bindCard();
        binding.map.invalidate();
    }

    private void renderMe() {
        if (!located) {
            return;
        }
        if (me == null) {
            me = new Marker(binding.map);
            me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            me.setInfoWindow(null);
            me.setIcon(MapSupport.emojiMarker(requireContext(), "🧍", ContextCompat.getColor(requireContext(), R.color.pastel_sky), true));
        }
        binding.map.getOverlays().remove(me);
        me.setPosition(new GeoPoint(latitude, longitude));
        binding.map.getOverlays().add(me);
    }

    /** Frames the member and the nearest few places, once per kind and location. */
    private void placeCamera() {
        if (cameraPlaced || shown.isEmpty() || binding.map.getWidth() == 0) {
            if (!cameraPlaced && binding.map.getWidth() == 0) {
                binding.map.post(this::placeCamera);
            }
            return;
        }
        double north = latitude;
        double south = latitude;
        double east = longitude;
        double west = longitude;
        for (int i = 0; i < Math.min(CAMERA_PLACES, shown.size()); i++) {
            Place place = shown.get(i).place();
            north = Math.max(north, place.latitude());
            south = Math.min(south, place.latitude());
            east = Math.max(east, place.longitude());
            west = Math.min(west, place.longitude());
        }
        double padLat = Math.max(0.004, (north - south) * 0.25);
        double padLng = Math.max(0.004, (east - west) * 0.25);
        binding.map.zoomToBoundingBox(new BoundingBox(north + padLat, east + padLng, south - padLat, west - padLng), false);
        cameraPlaced = true;
    }

    private void select(UUID id) {
        if (binding == null) {
            return;
        }
        selected = id;
        Context context = requireContext();
        for (PlacesService.Nearby nearby : shown) {
            Marker marker = markers.get(nearby.place().id());
            if (marker != null) {
                marker.setIcon(icon(context, nearby.place(), nearby.place().id().equals(id)));
            }
        }
        bindCard();
        if (id != null && markers.containsKey(id)) {
            binding.map.getController().animateTo(aboveCard(markers.get(id).getPosition()));
        }
        binding.map.invalidate();
    }

    /** The map centre that shows {@code point} in the upper part, clear of the place card. */
    private org.osmdroid.api.IGeoPoint aboveCard(GeoPoint point) {
        android.graphics.Point pixels = binding.map.getProjection().toPixels(point, null);
        int shift = Math.round(90 * getResources().getDisplayMetrics().density);
        return binding.map.getProjection().fromPixels(pixels.x, pixels.y + shift);
    }

    private void bindCard() {
        PlacesService.Nearby picked = null;
        for (PlacesService.Nearby nearby : shown) {
            if (nearby.place().id().equals(selected)) {
                picked = nearby;
            }
        }
        binding.mapCard.getRoot().setVisibility(picked == null ? View.GONE : View.VISIBLE);
        if (picked != null) {
            bind(binding.mapCard, picked);
        }
    }

    /** Uses the device location when allowed; {@code ask} shows the permission prompt. */
    private void locate(boolean ask) {
        Context context = requireContext();
        if (DeviceLocation.hasAny(context)) {
            DeviceLocation.current(context, new DeviceLocation.Callback() {
                @Override
                public void onLocation(Location location) {
                    if (binding == null) {
                        return;
                    }
                    if (!ListingLocation.inArea(location.getLatitude(), location.getLongitude())) {
                        if (ask) {
                            Ui.snack(binding.getRoot(), R.string.location_out_of_area);
                        }
                        return;
                    }
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    located = true;
                    cameraPlaced = false;
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
            locate(true);
        } else {
            Ui.snack(binding.getRoot(), R.string.location_denied);
        }
    }

    private void renderLocation() {
        binding.locationChip.setText(located ? R.string.places_location_mine : R.string.places_location_default);
    }

    /** Vector symbols render the same on every Android version; newer emoji show as empty boxes on older phones. */
    private static int symbol(Place place) {
        return place.kind() == Place.Kind.VET ? R.drawable.ic_vet : R.drawable.ic_paw;
    }

    /** Partners get a large marker with a sun-yellow ring, drawn above the others. */
    private static android.graphics.drawable.Drawable icon(Context context, Place place, boolean picked) {
        int ink = ContextCompat.getColor(context, R.color.brand_coral);
        if (place.sponsored()) {
            int ring = ContextCompat.getColor(context, picked ? R.color.brand_coral : R.color.brand_sun);
            return MapSupport.iconMarker(context, symbol(place), ink, ContextCompat.getColor(context, R.color.surface), ring, 68);
        }
        int fill = ContextCompat.getColor(context, place.kind() == Place.Kind.VET ? R.color.pastel_mint : R.color.pastel_peach);
        int ring = ContextCompat.getColor(context, picked ? R.color.brand_coral : R.color.surface);
        return MapSupport.iconMarker(context, symbol(place), ink, fill, ring, 48);
    }

    /** The large partner card at the top of the list. */
    private void bindSponsored(ItemPlaceSponsoredBinding view, PlacesService.Nearby nearby) {
        Context context = view.getRoot().getContext();
        Place place = nearby.place();
        view.nameView.setText(place.name());
        view.addressView.setText(place.address().orElse(""));
        view.addressView.setVisibility(place.address().isPresent() ? View.VISIBLE : View.GONE);
        view.descriptionView.setText(place.description().orElse(""));
        view.descriptionView.setVisibility(place.description().isPresent() ? View.VISIBLE : View.GONE);
        view.hoursView.setText(place.open24h() ? context.getString(R.string.places_open_24h)
                : place.openingHours().map(hours -> context.getString(R.string.places_hours, Labels.hours(context, hours))).orElse(""));
        if (nearby.openNow().isPresent() && !place.open24h()) {
            boolean open = nearby.openNow().get();
            view.statusBadge.setVisibility(View.VISIBLE);
            view.statusBadge.setText(open ? R.string.places_open_now : R.string.places_closed_now);
            view.statusBadge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                    open ? R.color.pastel_success : R.color.surface_muted)));
            view.statusBadge.setTextColor(ContextCompat.getColor(context, open ? R.color.success : R.color.text_secondary));
        } else {
            view.statusBadge.setVisibility(View.GONE);
        }
        view.distanceView.setText(Labels.distance(context, nearby.meters()));
        view.directionsButton.setOnClickListener(button -> Contact.directions(context, place.latitude(), place.longitude()));
        view.callButton.setVisibility(place.phone().isPresent() ? View.VISIBLE : View.GONE);
        place.phone().ifPresent(phone -> view.callButton.setOnClickListener(button -> Contact.dial(context, phone)));
        view.websiteButton.setVisibility(place.website().isPresent() ? View.VISIBLE : View.GONE);
        place.website().ifPresent(url -> view.websiteButton.setOnClickListener(button -> Contact.web(context, url)));
        view.getRoot().setOnClickListener(card -> {
            binding.viewGroup.check(R.id.viewMap);
            select(place.id());
        });
    }

    /** Shared by the list rows and the card over the map. */
    private void bind(ItemPlaceBinding view, PlacesService.Nearby nearby) {
        Context context = view.getRoot().getContext();
        Place place = nearby.place();
        view.iconView.setImageResource(symbol(place));
        view.iconView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                place.kind() == Place.Kind.VET ? R.color.pastel_mint : R.color.pastel_peach)));
        view.nameView.setText(place.sponsored() ? "★ " + place.name() : place.name());
        if (place.open24h()) {
            badge(view, R.string.places_open_24h, R.color.pastel_peach, R.color.brand_coral_dark);
        } else if (nearby.openNow().isPresent()) {
            boolean open = nearby.openNow().get();
            badge(view, open ? R.string.places_open_now : R.string.places_closed_now,
                    open ? R.color.pastel_success : R.color.surface_muted, open ? R.color.success : R.color.text_secondary);
        } else {
            view.statusBadge.setVisibility(View.GONE);
        }
        view.verifiedBadge.setVisibility(place.verified() ? View.VISIBLE : View.GONE);
        List<String> details = new ArrayList<>();
        place.address().ifPresent(details::add);
        if (!place.open24h()) {
            place.openingHours().ifPresent(hours -> details.add(context.getString(R.string.places_hours, Labels.hours(context, hours))));
        }
        view.detailView.setText(details.isEmpty() ? context.getString(R.string.places_no_details) : String.join("\n", details));
        view.distanceView.setText(Labels.distance(context, nearby.meters()));
        view.directionsButton.setOnClickListener(button -> Contact.directions(context, place.latitude(), place.longitude()));
        view.callButton.setVisibility(place.phone().isPresent() ? View.VISIBLE : View.GONE);
        place.phone().ifPresent(phone -> view.callButton.setOnClickListener(button -> Contact.dial(context, phone)));
        view.whatsappButton.setVisibility(place.whatsapp().isPresent() ? View.VISIBLE : View.GONE);
        place.whatsapp().ifPresent(digits -> view.whatsappButton.setOnClickListener(button ->
                Contact.whatsApp(context, digits, context.getString(R.string.places_whatsapp_message))));
        view.getRoot().setOnClickListener(card -> {
            if (mapMode) {
                Contact.directions(context, place.latitude(), place.longitude());
            } else {
                binding.viewGroup.check(R.id.viewMap);
                select(place.id());
            }
        });
    }

    private static void badge(ItemPlaceBinding view, int text, int fill, int ink) {
        Context context = view.getRoot().getContext();
        view.statusBadge.setVisibility(View.VISIBLE);
        view.statusBadge.setText(text);
        view.statusBadge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, fill)));
        view.statusBadge.setTextColor(ContextCompat.getColor(context, ink));
    }

    /** The OpenStreetMap credit as the last row, clear of the walk button over the tab bar. */
    private static final class Attribution extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.widget.TextView text = new android.widget.TextView(parent.getContext());
            text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int padding = parent.getResources().getDimensionPixelSize(R.dimen.space_md);
            text.setPadding(padding, padding, padding, 0);
            text.setGravity(android.view.Gravity.CENTER);
            text.setText(R.string.places_attribution);
            text.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.text_secondary));
            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
            return new RecyclerView.ViewHolder(text) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }

    private final class Places extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int PLAIN = 0;
        private static final int PARTNER = 1;
        private final List<PlacesService.Nearby> rows = new ArrayList<>();

        void replace(List<PlacesService.Nearby> next) {
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).place().sponsored() ? PARTNER : PLAIN;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == PARTNER) {
                return new Partner(ItemPlaceSponsoredBinding.inflate(inflater, parent, false));
            }
            return new Holder(ItemPlaceBinding.inflate(inflater, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof Partner partner) {
                bindSponsored(partner.binding, rows.get(position));
            } else {
                bind(((Holder) holder).binding, rows.get(position));
            }
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

        final class Partner extends RecyclerView.ViewHolder {
            final ItemPlaceSponsoredBinding binding;

            Partner(ItemPlaceSponsoredBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
