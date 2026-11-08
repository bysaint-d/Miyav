package az.miyav.domain.location;

import az.miyav.common.error.ValidationException;

/**
 * Approximate public location of a listing. Coordinates are rounded to three decimals (about 100 m),
 * the same precision the database column enforces, so a home address is never published.
 */
public final class ListingLocation {
    public static final double MIN_LATITUDE = 38;
    public static final double MAX_LATITUDE = 42;
    public static final double MIN_LONGITUDE = 44;
    public static final double MAX_LONGITUDE = 51;

    private final double latitude;
    private final double longitude;
    private final String name;

    public ListingLocation(double latitude, double longitude, String name) {
        if (!inArea(latitude, longitude)) {
            throw new ValidationException("location.out_of_area");
        }
        this.latitude = round(latitude);
        this.longitude = round(longitude);
        String trimmed = name == null ? "" : name.trim();
        this.name = trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    public static boolean inArea(double latitude, double longitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE
                && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    public String name() {
        return name;
    }

    /** Great-circle distance in metres. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(a));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
