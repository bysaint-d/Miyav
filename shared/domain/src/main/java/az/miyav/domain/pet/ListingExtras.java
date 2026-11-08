package az.miyav.domain.pet;

import az.miyav.domain.location.ListingLocation;

/** Optional listing data that is not part of the species polymorphism: vaccination and location. */
public final class ListingExtras {
    public static final ListingExtras NONE = new ListingExtras(Vaccination.UNKNOWN, null);

    private final Vaccination vaccination;
    private final ListingLocation location;

    public ListingExtras(Vaccination vaccination, ListingLocation location) {
        this.vaccination = vaccination == null ? Vaccination.UNKNOWN : vaccination;
        this.location = location;
    }

    public Vaccination vaccination() {
        return vaccination;
    }

    public ListingLocation location() {
        return location;
    }
}
