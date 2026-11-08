package az.miyav.domain.map;

import az.miyav.domain.location.GeoPoint;

import java.util.UUID;

/** A person offering to walk pets, placed at the area they chose for their profile (about 1 km). */
public final class WalkHelperPoint extends MapPoint {
    private final boolean verified;

    public WalkHelperPoint(UUID id, String name, String area, GeoPoint position, boolean verified) {
        super(id, name, area, position);
        this.verified = verified;
    }

    public boolean verified() {
        return verified;
    }

    @Override
    public MapPointKind kind() {
        return MapPointKind.WALK_HELPER;
    }

    @Override
    public int privacyRadiusMeters() {
        return 1000;
    }
}
