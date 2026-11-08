package az.miyav.application;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.ListingExtras;
import az.miyav.domain.pet.Vaccination;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetFactory;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.repository.MediaRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.validation.PetValidator;
import az.miyav.security.RoleGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PetService {
    private static final int MAX_JPEG_BYTES = 2 * 1024 * 1024;
    private final PetRepository pets;
    private final MediaRepository media;
    private final PetValidator validator;
    private final RoleGuard access;

    public PetService(PetRepository pets, PetValidator validator) {
        this(pets, new UnavailableMedia(), validator, new RoleGuard());
    }

    public PetService(PetRepository pets, MediaRepository media, PetValidator validator, RoleGuard access) {
        this.pets = pets;
        this.media = media;
        this.validator = validator;
        this.access = access;
    }

    public List<Pet> discover(PetQuery query, AuthSession session) {
        if (query == null || query.limit() < 1 || query.limit() > 50 || query.offset() < 0) {
            throw new ValidationException("validation.pet");
        }
        if (!query.city().isEmpty() && !Pet.SUPPORTED_CITIES.contains(query.city())) {
            throw new ValidationException("validation.city");
        }
        return pets.findPublished(query, session).stream().peek(validator::validate).toList();
    }

    public Optional<Pet> find(UUID id, AuthSession session) {
        return pets.findById(id, session);
    }

    /** Published listings within {@code radiusKm} of a point, nearest first. */
    public List<Pet> near(AuthSession session, double latitude, double longitude, int radiusKm, PetQuery query) {
        if (query == null || query.limit() < 1 || query.limit() > 50 || query.offset() < 0 || radiusKm < 1 || radiusKm > 100) {
            throw new ValidationException("validation.pet");
        }
        if (!ListingLocation.inArea(latitude, longitude)) {
            throw new ValidationException("location.out_of_area");
        }
        return pets.findNear(latitude, longitude, radiusKm, query, session).stream().peek(validator::validate).toList();
    }

    /** Other published listings of the same publisher. */
    public List<Pet> byPublisher(AuthSession session, UUID ownerId) {
        return pets.findPublishedByOwner(ownerId, session);
    }

    public Pet createDraft(
            AuthSession session,
            PetSpecies species,
            String name,
            String breed,
            int ageMonths,
            PetGender gender,
            String city,
            String description,
            Vaccination vaccination,
            ListingLocation location
    ) {
        requireSession(session);
        Pet pet = PetFactory.create(
                species,
                UUID.randomUUID(),
                session.userId(),
                name,
                breed,
                ageMonths,
                gender,
                city,
                description,
                PetStatus.DRAFT,
                List.of(),
                new ListingExtras(vaccination, location)
        );
        validator.validate(pet);
        return pets.save(session, pet);
    }

    public Pet update(
            AuthSession session,
            Pet existing,
            String name,
            String breed,
            int ageMonths,
            PetGender gender,
            String city,
            String description,
            PetStatus status,
            Vaccination vaccination,
            ListingLocation location
    ) {
        requireOwner(session, existing);
        Pet updated = existing
                .withDetails(name, breed, ageMonths, gender, city, description, status, existing.photoPaths())
                .withExtras(new ListingExtras(vaccination, location));
        validator.validate(updated);
        return pets.save(session, updated);
    }

    public List<Pet> mine(AuthSession session) {
        requireSession(session);
        return pets.findOwned(session);
    }

    public Pet createDraft(
            AuthSession session,
            PetSpecies species,
            String name,
            String breed,
            int ageMonths,
            PetGender gender,
            String city,
            String description
    ) {
        requireSession(session);
        Pet pet = PetFactory.create(
                species,
                UUID.randomUUID(),
                session.userId(),
                name,
                breed,
                ageMonths,
                gender,
                city,
                description,
                PetStatus.DRAFT,
                List.of()
        );
        validator.validate(pet);
        return pets.save(session, pet);
    }

    public Pet update(
            AuthSession session,
            Pet existing,
            String name,
            String breed,
            int ageMonths,
            PetGender gender,
            String city,
            String description,
            PetStatus status
    ) {
        requireOwner(session, existing);
        Pet updated = existing.withDetails(name, breed, ageMonths, gender, city, description, status, existing.photoPaths());
        validator.validate(updated);
        return pets.save(session, updated);
    }

    public Pet addPhoto(AuthSession session, Pet existing, byte[] jpeg) {
        requireOwner(session, existing);
        if (jpeg == null || jpeg.length == 0 || jpeg.length > MAX_JPEG_BYTES) {
            throw new ValidationException("media.too_large");
        }
        if (existing.photoPaths().size() >= 5) {
            throw new ValidationException("pet.photo_limit");
        }
        String path = media.uploadAdoptionJpeg(session, existing.id(), jpeg);
        List<String> photos = new ArrayList<>(existing.photoPaths());
        photos.add(path);
        Pet updated = existing.withPhotos(photos);
        validator.validate(updated);
        return pets.save(session, updated);
    }

    public Pet removePhoto(AuthSession session, Pet existing, String path) {
        requireOwner(session, existing);
        if (!existing.photoPaths().contains(path)) {
            throw new ValidationException("validation.pet");
        }
        List<String> photos = new ArrayList<>(existing.photoPaths());
        photos.remove(path);
        if (existing.status() == PetStatus.PUBLISHED && photos.isEmpty()) {
            throw new ValidationException("pet.photo_required");
        }
        Pet saved = pets.save(session, existing.withPhotos(photos));
        try {
            media.deleteAdoptionPhoto(session, path);
        } catch (RuntimeException ignored) {
            // The listing no longer references the file; storage cleanup can be retried later.
        }
        return saved;
    }

    public Pet publish(AuthSession session, Pet existing) {
        requireOwner(session, existing);
        if (existing.photoPaths().isEmpty()) {
            throw new ValidationException("pet.photo_required");
        }
        if (existing.location() == null) {
            throw new ValidationException("pet.location_required");
        }
        Pet published = existing.withStatus(PetStatus.PUBLISHED);
        validator.validate(published);
        return pets.save(session, published);
    }

    public Pet archive(AuthSession session, Pet existing) {
        requireOwner(session, existing);
        return pets.save(session, existing.withStatus(PetStatus.ARCHIVED));
    }

    public byte[] photo(AuthSession session, String path) {
        requireSession(session);
        return media.download(session, path);
    }

    private void requireOwner(AuthSession session, Pet pet) {
        requireSession(session);
        access.requireSelf(session.userId(), pet.ownerId());
    }

    private static void requireSession(AuthSession session) {
        if (session == null || session.userId() == null) {
            throw new az.miyav.common.error.AccessDeniedException("auth.failed");
        }
    }

    private static final class UnavailableMedia implements MediaRepository {
        @Override
        public String uploadAdoptionJpeg(AuthSession session, UUID listingId, byte[] jpeg) {
            throw new az.miyav.common.error.PersistenceException("schema.not_ready");
        }

        @Override
        public byte[] download(AuthSession session, String path) {
            throw new az.miyav.common.error.PersistenceException("schema.not_ready");
        }
    }
}
