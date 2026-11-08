package az.miyav.domain.map;

import az.miyav.domain.location.GeoPoint;

import java.util.Objects;
import java.util.UUID;

/**
 * Something shown on the NearMe map. Every point was shared on purpose by its owner; subclasses decide
 * how precise the shared position is and how the point is described.
 */
public abstract class MapPoint {
    private final UUID id;
    private final String title;
    private final String subtitle;
    private final GeoPoint position;

    protected MapPoint(UUID id, String title, String subtitle, GeoPoint position) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.position = Objects.requireNonNull(position, "position");
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public GeoPoint position() {
        return position;
    }

    public abstract MapPointKind kind();

    /** How far the true location may be from the shown position, in metres. */
    public abstract int privacyRadiusMeters();

    /** Builds the right subclass for a kind reported by the server. */
    public static MapPoint of(MapPointKind kind, UUID id, String title, String subtitle, GeoPoint position, boolean verified) {
        return switch (kind) {
            case LIVE_WALK -> new LiveWalkPoint(id, title, subtitle, position);
            case WALK_HELPER -> new WalkHelperPoint(id, title, subtitle, position, verified);
            case ADOPTION -> new AdoptionPoint(id, title, subtitle, position);
        };
    }
}
