package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.pet.PetProfileDraft;

import java.util.List;
import java.util.UUID;

/** The signed-in member's own pets (table {@code pets}); RLS keeps them private to the owner. */
public interface OwnPetRepository {
    List<PetProfile> mine(AuthSession session);

    PetProfile create(AuthSession session, PetProfileDraft draft);

    PetProfile update(AuthSession session, UUID petId, PetProfileDraft draft);

    void delete(AuthSession session, UUID petId);

    /** Uploads a JPEG to the private {@code pets} bucket and makes it the pet's photo. */
    PetProfile setPhoto(AuthSession session, UUID petId, byte[] jpeg);

    /** The owner's photos, or a photo of a pet that is on a walk the caller can see. */
    byte[] photo(AuthSession session, String path);
}
