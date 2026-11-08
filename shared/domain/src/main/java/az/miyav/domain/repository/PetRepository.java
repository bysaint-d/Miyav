package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PetRepository {
    List<Pet> findPublished(PetQuery query, AuthSession session);

    Optional<Pet> findById(UUID id, AuthSession session);

    List<Pet> findOwned(AuthSession session);

    List<Pet> findForAdmin(AuthSession session);

    Pet save(AuthSession session, Pet pet);

    default List<Pet> findNear(double latitude, double longitude, int radiusKm, PetQuery query, AuthSession session) {
        return List.of();
    }

    default List<Pet> findPublishedByOwner(UUID ownerId, AuthSession session) {
        return List.of();
    }
}
