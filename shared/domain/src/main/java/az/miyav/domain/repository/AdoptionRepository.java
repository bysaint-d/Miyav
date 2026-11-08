package az.miyav.domain.repository;

import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.auth.AuthSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdoptionRepository {
    AdoptionApplication save(AuthSession session, AdoptionApplication application);

    Optional<AdoptionApplication> findAcceptedForPet(AuthSession session, UUID petId);

    List<AdoptionApplication> findByPet(AuthSession session, UUID petId);

    List<AdoptionApplication> findByApplicant(AuthSession session);

    List<AdoptionApplication> findForAdmin(AuthSession session);
}
