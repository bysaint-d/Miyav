package az.miyav.domain.pet;

/** Listing order. "Nearby" search always orders by distance and ignores this. */
public enum PetSort {
    NEWEST,
    OLDEST,
    YOUNGEST,
    ELDEST
}
