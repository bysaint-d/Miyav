package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.common.time.TimeSource;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.pet.Dog;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.AdoptionRepository;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.validation.PetValidator;
import az.miyav.security.RoleGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdoptionServiceTest {
    private final Instant now = Instant.parse("2026-11-08T00:00:00Z");
    private final MemoryAdoptions adoptions = new MemoryAdoptions();
    private AdoptionService service;

    @BeforeEach
    void setUp() {
        TimeSource time = () -> now;
        service = new AdoptionService(adoptions, new PetValidator(), new RoleGuard(), time);
    }

    @Test
    void applicantCannotApplyToOwnOrClosedListing() {
        User owner = user();
        Pet own = dog(owner.id(), PetStatus.PUBLISHED);
        assertThrows(ValidationException.class, () -> service.submit(session(owner), owner, own, "Mən bu itə ev vermək istəyirəm."));
        Pet draft = dog(UUID.randomUUID(), PetStatus.DRAFT);
        assertThrows(ValidationException.class, () -> service.submit(session(owner), owner, draft, "Mən bu itə ev vermək istəyirəm."));
    }

    @Test
    void ownerAcceptsOnePendingApplicationAndASecondAcceptIsRejected() {
        User owner = user();
        User applicant = user();
        Pet pet = dog(owner.id(), PetStatus.PUBLISHED);
        AdoptionApplication first = service.submit(session(applicant), applicant, pet, "Mən bu itə ev vermək istəyirəm.");
        AdoptionApplication accepted = service.accept(session(owner), owner, pet, first);
        assertEquals(ApplicationStatus.ACCEPTED, accepted.status());
        AdoptionApplication second = new AdoptionApplication(
                UUID.randomUUID(),
                pet.id(),
                user().id(),
                "İkinci ailə də müraciət edir burada.",
                ApplicationStatus.PENDING,
                now,
                now
        );
        assertThrows(ValidationException.class, () -> service.accept(session(owner), owner, pet, second));
    }

    @Test
    void onlyTheApplicantCancelsAndOnlyTheOwnerDecides() {
        User owner = user();
        User applicant = user();
        User stranger = user();
        Pet pet = dog(owner.id(), PetStatus.PUBLISHED);
        AdoptionApplication pending = service.submit(session(applicant), applicant, pet, "Mən bu itə ev vermək istəyirəm.");
        assertThrows(AccessDeniedException.class, () -> service.accept(session(stranger), stranger, pet, pending));
        assertThrows(AccessDeniedException.class, () -> service.cancel(session(stranger), stranger, pending));
        assertThrows(AccessDeniedException.class, () -> service.submit(session(stranger), applicant, pet, "Mən bu itə ev vermək istəyirəm."));
        assertEquals(ApplicationStatus.CANCELLED, service.cancel(session(applicant), applicant, pending).status());
    }

    private static User user() {
        return new User(UUID.randomUUID(), "a@example.com", "Aysel Məmmədova", UserRole.OWNER, false, "Bakı", "", "", LocaleCode.AZ);
    }

    private AuthSession session(User user) {
        return new AuthSession(user.id(), user.email(), "token", "refresh", now, user.role());
    }

    private static Pet dog(UUID ownerId, PetStatus status) {
        return new Dog(
                UUID.randomUUID(),
                ownerId,
                "Boncuk",
                "mix",
                12,
                PetGender.FEMALE,
                "Bakı",
                "Sakit və mehriban bir itdir.",
                status,
                status == PetStatus.PUBLISHED ? List.of("photo.jpg") : List.of()
        );
    }

    private static final class MemoryAdoptions implements AdoptionRepository {
        private final List<AdoptionApplication> saved = new ArrayList<>();

        @Override
        public AdoptionApplication save(AuthSession session, AdoptionApplication application) {
            saved.removeIf(existing -> existing.id().equals(application.id()));
            saved.add(application);
            return application;
        }

        @Override
        public Optional<AdoptionApplication> findAcceptedForPet(AuthSession session, UUID petId) {
            return saved.stream()
                    .filter(application -> application.petId().equals(petId) && application.status() == ApplicationStatus.ACCEPTED)
                    .findFirst();
        }

        @Override
        public List<AdoptionApplication> findByPet(AuthSession session, UUID petId) {
            return saved.stream().filter(application -> application.petId().equals(petId)).toList();
        }

        @Override
        public List<AdoptionApplication> findByApplicant(AuthSession session) {
            return saved.stream().filter(application -> application.applicantId().equals(session.userId())).toList();
        }

        @Override
        public List<AdoptionApplication> findForAdmin(AuthSession session) {
            return List.copyOf(saved);
        }
    }
}
