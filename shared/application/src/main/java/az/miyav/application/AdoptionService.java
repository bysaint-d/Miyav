package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.common.time.TimeSource;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.repository.AdoptionRepository;
import az.miyav.domain.user.User;
import az.miyav.domain.validation.PetValidator;
import az.miyav.security.RoleGuard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdoptionService {
    private final AdoptionRepository adoptions;
    private final PetValidator pets;
    private final RoleGuard access;
    private final TimeSource time;

    public AdoptionService(AdoptionRepository adoptions, PetValidator pets, RoleGuard access, TimeSource time) {
        this.adoptions = adoptions;
        this.pets = pets;
        this.access = access;
        this.time = time;
    }

    public AdoptionApplication submit(AuthSession session, User applicant, Pet pet, String message) {
        requireActor(session, applicant.id());
        pets.validate(pet);
        if (pet.status() != PetStatus.PUBLISHED) {
            throw new ValidationException("adoption.listing_unavailable");
        }
        if (pet.ownerId().equals(applicant.id())) {
            throw new ValidationException("adoption.own_listing");
        }
        if (adoptions.findAcceptedForPet(session, pet.id()).isPresent()) {
            throw new ValidationException("adoption.already_accepted");
        }
        Instant now = time.now();
        AdoptionApplication application = new AdoptionApplication(
                UUID.randomUUID(),
                pet.id(),
                applicant.id(),
                message,
                ApplicationStatus.PENDING,
                now,
                now
        );
        return adoptions.save(session, application);
    }

    public AdoptionApplication accept(AuthSession session, User actor, Pet pet, AdoptionApplication application) {
        requireActor(session, actor.id());
        requirePendingOwnerAction(actor, pet, application);
        if (adoptions.findAcceptedForPet(session, pet.id()).isPresent()) {
            throw new ValidationException("adoption.already_accepted");
        }
        return adoptions.save(session, application.withStatus(ApplicationStatus.ACCEPTED, time.now()));
    }

    public AdoptionApplication reject(AuthSession session, User actor, Pet pet, AdoptionApplication application) {
        requireActor(session, actor.id());
        requirePendingOwnerAction(actor, pet, application);
        return adoptions.save(session, application.withStatus(ApplicationStatus.REJECTED, time.now()));
    }

    public AdoptionApplication cancel(AuthSession session, User actor, AdoptionApplication application) {
        requireActor(session, actor.id());
        if (!actor.id().equals(application.applicantId())) {
            throw new AccessDeniedException("access.owner");
        }
        if (application.status() != ApplicationStatus.PENDING) {
            throw new ValidationException("adoption.not_pending");
        }
        return adoptions.save(session, application.withStatus(ApplicationStatus.CANCELLED, time.now()));
    }

    public List<AdoptionApplication> mine(AuthSession session) {
        requireActor(session, session.userId());
        return adoptions.findByApplicant(session);
    }

    public List<AdoptionApplication> forPet(AuthSession session, Pet pet) {
        requireActor(session, session.userId());
        access.requireSelf(session.userId(), pet.ownerId());
        return adoptions.findByPet(session, pet.id());
    }

    private void requireActor(AuthSession session, UUID userId) {
        if (session == null || !userId.equals(session.userId())) {
            throw new AccessDeniedException("access.owner");
        }
    }

    private void requirePendingOwnerAction(User actor, Pet pet, AdoptionApplication application) {
        access.requireSelf(actor.id(), pet.ownerId());
        if (!application.petId().equals(pet.id())) {
            throw new ValidationException("adoption.pet_mismatch");
        }
        if (application.status() != ApplicationStatus.PENDING) {
            throw new ValidationException("adoption.not_pending");
        }
    }
}
