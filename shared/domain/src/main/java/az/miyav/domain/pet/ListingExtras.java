package az.miyav.domain.pet;

import az.miyav.domain.location.ListingLocation;

import java.time.Instant;
import java.util.Optional;

/**
 * Optional listing data that is not part of the species polymorphism: vaccination, location and the end
 * of a paid premium period (set by the server only).
 */
public final class ListingExtras {
    public static final ListingExtras NONE = new ListingExtras(Vaccination.UNKNOWN, null);

    private final Vaccination vaccination;
    private final ListingLocation location;
    private final Instant premiumUntil;

    public ListingExtras(Vaccination vaccination, ListingLocation location) {
        this(vaccination, location, null);
    }

    public ListingExtras(Vaccination vaccination, ListingLocation location, Instant premiumUntil) {
        this.vaccination = vaccination == null ? Vaccination.UNKNOWN : vaccination;
        this.location = location;
        this.premiumUntil = premiumUntil;
    }

    public Vaccination vaccination() {
        return vaccination;
    }

    public ListingLocation location() {
        return location;
    }

    public Optional<Instant> premiumUntil() {
        return Optional.ofNullable(premiumUntil);
    }

    public boolean premium(Instant now) {
        return premiumUntil != null && premiumUntil.isAfter(now);
    }
}
