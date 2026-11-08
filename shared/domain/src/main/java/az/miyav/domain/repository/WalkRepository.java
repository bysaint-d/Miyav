package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.walk.WalkPet;
import az.miyav.domain.walk.WalkSession;
import az.miyav.domain.walk.Walker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalkRepository {
    WalkSession start(AuthSession session, PetSpecies species, String petName, String message, double latitude, double longitude);

    /** Starts a walk with some of the caller's own pets and/or the pet of a walk job assigned to them. */
    WalkSession startWith(AuthSession session, List<UUID> petIds, UUID walkListingId, String message,
                          double latitude, double longitude);

    /** The pets on a walk; empty unless the caller is that walker or walking nearby. */
    List<WalkPet> pets(AuthSession session, UUID sessionId);

    WalkSession updateLocation(AuthSession session, double latitude, double longitude);

    WalkSession setStatus(AuthSession session, WalkSession.Status status);

    Optional<WalkSession> current(AuthSession session);

    List<Walker> nearby(AuthSession session);
}
