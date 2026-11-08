package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.pet.PetProfileDraft;
import az.miyav.domain.repository.OwnPetRepository;

import java.util.List;
import java.util.UUID;

/** "Mənim heyvanlarım": the member's own pets, which they choose from when starting a walk. */
public final class MyPetsService {
    private final OwnPetRepository pets;

    public MyPetsService(OwnPetRepository pets) {
        this.pets = pets;
    }

    public List<PetProfile> mine(AuthSession session) {
        requireSession(session);
        return pets.mine(session);
    }

    public PetProfile save(AuthSession session, UUID petId, PetProfileDraft draft) {
        requireSession(session);
        return petId == null ? pets.create(session, draft) : pets.update(session, petId, draft);
    }

    public void delete(AuthSession session, PetProfile pet) {
        requireOwner(session, pet);
        pets.delete(session, pet.id());
    }

    public PetProfile setPhoto(AuthSession session, PetProfile pet, byte[] jpeg) {
        requireOwner(session, pet);
        return pets.setPhoto(session, pet.id(), jpeg);
    }

    public byte[] photo(AuthSession session, String path) {
        requireSession(session);
        return pets.photo(session, path);
    }

    private static void requireOwner(AuthSession session, PetProfile pet) {
        requireSession(session);
        if (!pet.ownerId().equals(session.userId())) {
            throw new AccessDeniedException("access.owner");
        }
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }
}
