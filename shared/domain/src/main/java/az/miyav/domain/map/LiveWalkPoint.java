package az.miyav.domain.map;

import az.miyav.domain.location.GeoPoint;

import java.util.UUID;

/** Someone walking right now with NearMe sharing on. The server rounds the position to about 1 km. */
public final class LiveWalkPoint extends MapPoint {
    public LiveWalkPoint(UUID id, String walkerName, String petLabel, GeoPoint position) {
        super(id, walkerName, petLabel, position);
    }

    @Override
    public MapPointKind kind() {
        return MapPointKind.LIVE_WALK;
    }

    @Override
    public int privacyRadiusMeters() {
        return 1000;
    }
}
