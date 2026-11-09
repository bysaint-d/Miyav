package az.miyav.android.ui;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentWalkRouteBinding;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.GeoPoint;
import az.miyav.domain.walk.WalkHistoryEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** One past walk on the map: the route line, where it started and ended, distance, time and pace. */
public class WalkRouteFragment extends Fragment {
    private FragmentWalkRouteBinding binding;
    private WalkHistoryEntry walk;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWalkRouteBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        MapSupport.style(binding.map, true);
        binding.map.getController().setZoom(14.0);
        binding.map.getController().setCenter(MapSupport.DEFAULT_CAMERA);
        binding.deleteButton.setOnClickListener(view -> confirmDelete());
        load(UUID.fromString(requireArguments().getString("walkId")));
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
        binding.map.onDetach();
        binding = null;
        super.onDestroyView();
    }

    private void load(UUID walkId) {
        Context context = requireContext().getApplicationContext();
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.auth().requireSession();
            WalkHistoryEntry entry = app.walkingService().history(session).stream()
                    .filter(item -> item.id().equals(walkId)).findFirst()
                    .orElseThrow(() -> new az.miyav.common.error.ValidationException("walk.no_session"));
            List<GeoPoint> route = app.walkingService().route(session, walkId);
            String from = route.isEmpty() ? "" : place(context, route.get(0));
            String to = route.size() < 2 ? "" : place(context, route.get(route.size() - 1));
            return new Loaded(entry, route, from, to);
        }, this::show, code -> Ui.snack(binding.getRoot(), code));
    }

    private void show(Loaded loaded) {
        walk = loaded.walk;
        Context context = requireContext();
        binding.titleView.setText(walk.petName().isBlank() ? getString(R.string.walk_history_untitled) : walk.petName());
        binding.dateView.setText(Labels.date(walk.startedAt()));
        Duration duration = walk.duration(Instant.now());
        binding.distanceValue.setText(getString(R.string.walk_route_distance, WalkHistoryFragment.km(context, walk.distanceMeters())));
        binding.durationValue.setText(getString(R.string.walk_route_duration, WalkHistoryFragment.clock(duration)));
        double hours = duration.getSeconds() / 3600.0;
        binding.paceValue.setText(hours > 0.02 && walk.distanceMeters() > 0
                ? getString(R.string.walk_route_speed, String.format(Locale.US, "%.1f", walk.distanceMeters() / 1000.0 / hours))
                : "");
        binding.fromView.setText(getString(R.string.walk_route_from, loaded.from.isBlank() ? getString(R.string.walk_route_unknown) : loaded.from));
        binding.toView.setText(getString(R.string.walk_route_to, loaded.to.isBlank() ? getString(R.string.walk_route_unknown) : loaded.to));
        binding.fromView.setVisibility(loaded.route.isEmpty() ? View.GONE : View.VISIBLE);
        binding.toView.setVisibility(loaded.route.size() < 2 ? View.GONE : View.VISIBLE);
        binding.deleteButton.setVisibility(walk.open() ? View.GONE : View.VISIBLE);
        binding.noRoute.setVisibility(loaded.route.size() < 2 ? View.VISIBLE : View.GONE);
        drawRoute(loaded.route);
    }

    private void drawRoute(List<GeoPoint> route) {
        if (route.isEmpty()) {
            return;
        }
        Context context = requireContext();
        List<org.osmdroid.util.GeoPoint> points = new ArrayList<>();
        for (GeoPoint point : route) {
            points.add(new org.osmdroid.util.GeoPoint(point.latitude(), point.longitude()));
        }
        if (points.size() >= 2) {
            Polyline line = new Polyline(binding.map);
            line.setPoints(points);
            line.getOutlinePaint().setColor(ContextCompat.getColor(context, R.color.brand_coral));
            line.getOutlinePaint().setStrokeWidth(10 * getResources().getDisplayMetrics().density / 1.6f);
            line.getOutlinePaint().setStrokeCap(android.graphics.Paint.Cap.ROUND);
            line.setInfoWindow(null);
            binding.map.getOverlays().add(line);
        }
        binding.map.getOverlays().add(pin(points.get(0), "🟢"));
        if (points.size() >= 2) {
            binding.map.getOverlays().add(pin(points.get(points.size() - 1), "🏁"));
        }
        binding.map.post(() -> {
            if (binding == null) {
                return;
            }
            if (points.size() < 2) {
                binding.map.getController().setZoom(16.0);
                binding.map.getController().setCenter(points.get(0));
                return;
            }
            BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
            double pad = Math.max(0.0015, Math.max(box.getLatitudeSpan(), box.getLongitudeSpanWithDateLine()) * 0.2);
            binding.map.zoomToBoundingBox(new BoundingBox(box.getLatNorth() + pad, box.getLonEast() + pad,
                    box.getLatSouth() - pad, box.getLonWest() - pad), false);
        });
        binding.map.invalidate();
    }

    private Marker pin(org.osmdroid.util.GeoPoint point, String emoji) {
        Marker marker = new Marker(binding.map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setIcon(MapSupport.emojiMarker(requireContext(), emoji, ContextCompat.getColor(requireContext(), R.color.surface), false));
        marker.setInfoWindow(null);
        return marker;
    }

    /** A short street name for a point, from the phone's geocoder; empty when offline or unknown. */
    @SuppressWarnings("deprecation")
    private static String place(Context context, GeoPoint point) {
        if (!Geocoder.isPresent()) {
            return "";
        }
        try {
            List<Address> results = new Geocoder(context, Locale.getDefault()).getFromLocation(point.latitude(), point.longitude(), 1);
            if (results == null || results.isEmpty()) {
                return "";
            }
            Address address = results.get(0);
            String street = address.getThoroughfare();
            String area = address.getSubLocality() != null ? address.getSubLocality() : address.getLocality();
            if (street != null && area != null) {
                return street + ", " + area;
            }
            return street != null ? street : area == null ? "" : area;
        } catch (Exception exception) {
            return "";
        }
    }

    private void confirmDelete() {
        if (walk == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.walk_history_delete_confirm)
                .setPositiveButton(R.string.walk_history_delete, (dialog, which) -> Ui.run(this, () -> {
                    MiyavApplication app = Ui.app(this);
                    app.walkingService().deleteFromHistory(app.auth().requireSession(), walk);
                    return Boolean.TRUE;
                }, done -> Ui.nav(this).popBackStack(), code -> Ui.snack(binding.getRoot(), code)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static final class Loaded {
        final WalkHistoryEntry walk;
        final List<GeoPoint> route;
        final String from;
        final String to;

        Loaded(WalkHistoryEntry walk, List<GeoPoint> route, String from, String to) {
            this.walk = walk;
            this.route = route;
            this.from = from;
            this.to = to;
        }
    }
}
