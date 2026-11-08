package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.walk.WalkSession;
import az.miyav.domain.walk.Walker;

import java.util.List;
import java.util.Optional;

public interface WalkRepository {
    WalkSession start(AuthSession session, PetSpecies species, String petName, String message, double latitude, double longitude);

    WalkSession updateLocation(AuthSession session, double latitude, double longitude);

    WalkSession setStatus(AuthSession session, WalkSession.Status status);

    Optional<WalkSession> current(AuthSession session);

    List<Walker> nearby(AuthSession session);
}
