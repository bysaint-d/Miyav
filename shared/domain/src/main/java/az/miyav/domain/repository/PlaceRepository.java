package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.place.Place;

import java.util.List;

/** Public list of vets and pet shops; readable without an account. Admins maintain it. */
public interface PlaceRepository {
    List<Place> list(Place.Kind kind);

    /** Every place including hidden ones; RLS returns hidden rows to admins only. */
    default List<Place> listAll(AuthSession admin) {
        throw new UnsupportedOperationException("listAll");
    }

    /** Inserts or updates by id. */
    default Place save(AuthSession admin, Place place) {
        throw new UnsupportedOperationException("save");
    }
}
