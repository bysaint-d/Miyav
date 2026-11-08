package az.miyav.application;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.location.GeoPoint;

public final class LocationService {
    private static final CityCenter BAKU = new CityCenter("Bakı", 40.41, 49.87);
    private static final CityCenter SUMQAYIT = new CityCenter("Sumqayıt", 40.59, 49.67);
    private static final CityCenter ABSHERON = new CityCenter("Abşeron", 40.45, 49.75);

    public GeoPoint approximate(double latitude, double longitude) {
        if (latitude < 38 || latitude > 42 || longitude < 44 || longitude > 51) {
            throw new ValidationException("location.out_of_area");
        }
        return new GeoPoint(round(latitude), round(longitude));
    }

    public String nearestCity(double latitude, double longitude) {
        GeoPoint point = approximate(latitude, longitude);
        CityCenter nearest = BAKU;
        double best = distance(point, BAKU);
        double sumqayit = distance(point, SUMQAYIT);
        if (sumqayit < best) {
            nearest = SUMQAYIT;
            best = sumqayit;
        }
        if (distance(point, ABSHERON) < best) {
            nearest = ABSHERON;
        }
        return nearest.name;
    }

    private static double distance(GeoPoint point, CityCenter city) {
        double lat = point.latitude() - city.latitude;
        double lng = point.longitude() - city.longitude;
        return lat * lat + lng * lng;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record CityCenter(String name, double latitude, double longitude) {
    }
}
