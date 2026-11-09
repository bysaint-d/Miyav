package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.common.time.TimeSource;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.place.Place;
import az.miyav.domain.repository.PlaceRepository;
import az.miyav.domain.user.UserRole;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Vets and pet shops sorted by distance from the user. The list changes rarely, so it is cached briefly. */
public final class PlacesService {
    /** Used when the device location is unknown: central Baku. */
    public static final double DEFAULT_LATITUDE = 40.4093;
    public static final double DEFAULT_LONGITUDE = 49.8671;
    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
    private static final Duration CACHE = Duration.ofMinutes(15);

    /** Narrows the list: open right now, or open around the clock. */
    public enum Filter {
        ALL, OPEN_NOW, ALWAYS_OPEN
    }

    public static final class Nearby {
        private final Place place;
        private final double meters;
        private final Optional<Boolean> openNow;

        Nearby(Place place, double meters, Optional<Boolean> openNow) {
            this.place = place;
            this.meters = meters;
            this.openNow = openNow;
        }

        public Place place() {
            return place;
        }

        public double meters() {
            return meters;
        }

        /** Empty when the opening hours are unknown. */
        public Optional<Boolean> openNow() {
            return openNow;
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
        return nearest(kind, latitude, longitude, Filter.ALL);
    }

    public List<Nearby> nearest(Place.Kind kind, double latitude, double longitude, Filter filter) {
        LocalDateTime local = LocalDateTime.ofInstant(time.now(), BAKU);
        List<Nearby> result = new ArrayList<>();
        for (Place place : load(kind)) {
            Optional<Boolean> open = place.openAt(local);
            if (filter == Filter.OPEN_NOW && !open.orElse(false)) {
                continue;
            }
            if (filter == Filter.ALWAYS_OPEN && !place.open24h()) {
                continue;
            }
            result.add(new Nearby(place, ListingLocation.distanceMeters(latitude, longitude, place.latitude(), place.longitude()), open));
        }
        result.sort(Comparator.comparing((Nearby nearby) -> !nearby.place().sponsored()).thenComparingDouble(Nearby::meters));
        return result;
    }

    /** Drops the cache, for pull-to-refresh. */
    public void refresh() {
        cache.clear();
    }

    /** Admin console: every place, hidden ones included. */
    public List<Place> all(AuthSession admin) {
        requireAdmin(admin);
        return places.listAll(admin);
    }

    /** Admin console: add or edit a place; hiding is {@code active = false}. */
    public Place save(AuthSession admin, Place place) {
        requireAdmin(admin);
        if (place.name().isBlank() || place.name().length() > 120) {
            throw new ValidationException("place.name");
        }
        if (!ListingLocation.inArea(place.latitude(), place.longitude())) {
            throw new ValidationException("location.out_of_area");
        }
        if (place.whatsapp().isPresent() && !place.whatsapp().get().matches("[0-9]{9,15}")) {
            throw new ValidationException("validation.phone");
        }
        if (place.website().isPresent() && !place.website().get().startsWith("https://")) {
            throw new ValidationException("place.website");
        }
        if (place.description().map(String::length).orElse(0) > 300) {
            throw new ValidationException("place.description");
        }
        Place saved = places.save(admin, place);
        cache.clear();
        return saved;
    }

    private static void requireAdmin(AuthSession session) {
        if (session == null || session.role() != UserRole.ADMIN) {
            throw new AccessDeniedException("access.admin");
        }
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
