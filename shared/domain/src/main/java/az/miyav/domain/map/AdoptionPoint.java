package az.miyav.domain.map;

import az.miyav.domain.location.GeoPoint;

import java.util.UUID;

/** An active adoption listing; the owner picked the pin, stored to about 100 m. */
public final class AdoptionPoint extends MapPoint {
    public AdoptionPoint(UUID id, String petName, String description, GeoPoint position) {
        super(id, petName, description, position);
    }

    @Override
    public MapPointKind kind() {
        return MapPointKind.ADOPTION;
    }

    @Override
    public int privacyRadiusMeters() {
        return 100;
    }
}
