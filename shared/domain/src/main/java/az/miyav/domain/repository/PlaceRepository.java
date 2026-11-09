package az.miyav.domain.repository;

import az.miyav.domain.place.Place;

import java.util.List;

/** Public list of vets and pet shops; readable without an account. */
public interface PlaceRepository {
    List<Place> list(Place.Kind kind);
}
