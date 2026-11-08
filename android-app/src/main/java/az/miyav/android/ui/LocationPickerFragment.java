package az.miyav.android.ui;

import android.Manifest;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentLocationPickerBinding;
import az.miyav.android.location.DeviceLocation;
import az.miyav.common.error.AppException;
import az.miyav.domain.location.ListingLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lets the publisher place the listing on the map. The result is approximate (~100 m) by design. */
public class LocationPickerFragment extends Fragment {
    public static final String RESULT = "listing_location";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String NAME = "name";

    private FragmentLocationPickerBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable resolve = this::resolveName;
    private String placeName = "";
    private int resolveToken;
    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermission);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLocationPickerBinding.inflate(inflater, container, false);
        MapSupport.style(binding.map, true);
        binding.map.getController().setZoom(16.0);
        Bundle args = getArguments();
        if (args != null && args.containsKey(LATITUDE)) {
            binding.map.getController().setCenter(new GeoPoint(args.getDouble(LATITUDE), args.getDouble(LONGITUDE)));
        } else {
            binding.map.getController().setCenter(MapSupport.DEFAULT_CAMERA);
            binding.map.getController().setZoom(12.0);
            if (DeviceLocation.hasAny(requireContext())) {
                centerOnDevice();
            } else {
                explainPermission();
            }
        }
        binding.map.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                scheduleResolve();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                return false;
            }
        });
        binding.backButton.setOnClickListener(view -> Ui.nav(this).popBackStack());
        binding.myLocationButton.setOnClickListener(view -> {
            if (DeviceLocation.hasAny(requireContext())) {
                centerOnDevice();
            } else {
                explainPermission();
            }
        });
        binding.confirmButton.setOnClickListener(view -> confirm());
        scheduleResolve();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.map.onResume();
    }

    @Override
    public void onPause() {
        binding.map.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(resolve);
        binding.map.onDetach();
        binding = null;
        super.onDestroyView();
    }

    private void explainPermission() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.location_rationale_title)
                .setMessage(R.string.location_rationale_listing)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> permission.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onPermission(Map<String, Boolean> result) {
        if (binding == null) {
            return;
        }
        if (DeviceLocation.hasAny(requireContext())) {
            centerOnDevice();
        } else {
            Ui.snack(binding.getRoot(), R.string.location_denied);
        }
    }

    private void centerOnDevice() {
        DeviceLocation.current(requireContext(), new DeviceLocation.Callback() {
            @Override
            public void onLocation(Location location) {
                if (binding == null) {
                    return;
                }
                binding.map.getController().setZoom(16.0);
                binding.map.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));
                scheduleResolve();
            }

            @Override
            public void onFailure(DeviceLocation.Failure failure) {
                if (binding != null) {
                    Ui.snack(binding.getRoot(), failure == DeviceLocation.Failure.DISABLED
                            ? R.string.location_disabled : R.string.location_unavailable);
                }
            }
        });
    }

    private void scheduleResolve() {
        handler.removeCallbacks(resolve);
        if (binding != null) {
            binding.placeName.setText(R.string.location_picker_resolving);
        }
        handler.postDelayed(resolve, 600);
    }

    private void resolveName() {
        if (binding == null) {
            return;
        }
        GeoPoint center = (GeoPoint) binding.map.getMapCenter();
        double latitude = center.getLatitude();
        double longitude = center.getLongitude();
        int token = ++resolveToken;
        if (!ListingLocation.inArea(latitude, longitude)) {
            placeName = "";
            binding.placeName.setText(R.string.location_out_of_area);
            return;
        }
        Ui.run(this, () -> areaName(latitude, longitude), name -> {
            if (token != resolveToken) {
                return;
            }
            placeName = name;
            binding.placeName.setText(name);
        }, code -> binding.placeName.setText(R.string.pet_location_none));
    }

    /** District/city level only: a street or house number is never stored with a listing. */
    private String areaName(double latitude, double longitude) {
        String fallback;
        try {
            fallback = Ui.app(this).locationService().nearestCity(latitude, longitude);
        } catch (AppException exception) {
            fallback = "";
        }
        if (!Geocoder.isPresent()) {
            return fallback;
        }
        try {
            List<Address> results = new Geocoder(requireContext(), Locale.getDefault()).getFromLocation(latitude, longitude, 1);
            if (results == null || results.isEmpty()) {
                return fallback;
            }
            Address address = results.get(0);
            String district = address.getSubLocality() != null ? address.getSubLocality() : address.getSubAdminArea();
            String city = address.getLocality() != null ? address.getLocality() : address.getAdminArea();
            if (district != null && city != null && !district.equals(city)) {
                return district + ", " + city;
            }
            return city != null ? city : (district != null ? district : fallback);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private void confirm() {
        GeoPoint center = (GeoPoint) binding.map.getMapCenter();
        try {
            ListingLocation location = new ListingLocation(center.getLatitude(), center.getLongitude(), placeName);
            Bundle result = new Bundle();
            result.putDouble(LATITUDE, location.latitude());
            result.putDouble(LONGITUDE, location.longitude());
            result.putString(NAME, location.name());
            getParentFragmentManager().setFragmentResult(RESULT, result);
            Ui.nav(this).popBackStack();
        } catch (AppException exception) {
            Ui.snack(binding.getRoot(), exception.code());
        }
    }
}
