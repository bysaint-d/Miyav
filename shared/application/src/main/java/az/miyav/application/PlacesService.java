package az.miyav.application;

import az.miyav.common.time.TimeSource;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.place.Place;
import az.miyav.domain.repository.PlaceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Vets and pet shops sorted by distance from the user. The list changes rarely, so it is cached briefly. */
public final class PlacesService {
    /** Used when the device location is unknown: central Baku. */
    public static final double DEFAULT_LATITUDE = 40.4093;
    public static final double DEFAULT_LONGITUDE = 49.8671;
    private static final Duration CACHE = Duration.ofMinutes(15);

    public static final class Nearby {
        private final Place place;
        private final double meters;

        Nearby(Place place, double meters) {
            this.place = place;
            this.meters = meters;
        }

        public Place place() {
            return place;
        }

        public double meters() {
            return meters;
        }
    }

    private final PlaceRepository places;
    private final TimeSource time;
    private final Map<Place.Kind, Cached> cache = new ConcurrentHashMap<>();

    public PlacesService(PlaceRepository places, TimeSource time) {
        this.places = places;
        this.time = time;
    }

    public List<Nearby> nearest(Place.Kind kind, double latitude, double longitude) {
        List<Nearby> result = new ArrayList<>();
        for (Place place : load(kind)) {
            result.add(new Nearby(place, ListingLocation.distanceMeters(latitude, longitude, place.latitude(), place.longitude())));
        }
        result.sort(Comparator.comparingDouble(Nearby::meters));
        return result;
    }

    /** Drops the cache, for pull-to-refresh. */
    public void refresh() {
        cache.clear();
    }

    private List<Place> load(Place.Kind kind) {
        Instant now = time.now();
        Cached cached = cache.get(kind);
        if (cached != null && cached.loadedAt.plus(CACHE).isAfter(now)) {
            return cached.places;
        }
        List<Place> fresh = List.copyOf(places.list(kind));
        cache.put(kind, new Cached(fresh, now));
        return fresh;
    }

    private static final class Cached {
        final List<Place> places;
        final Instant loadedAt;

        Cached(List<Place> places, Instant loadedAt) {
            this.places = places;
            this.loadedAt = loadedAt;
        }
    }
}
