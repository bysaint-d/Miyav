package az.miyav.application;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.repository.MediaRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.repository.PublicCatalogRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.validation.PetValidator;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Public browsing: discover, search, filter, listing details, publisher cards and photos. A {@code null}
 * viewer is a guest and is served only by the anonymous catalog. A signed-in viewer reads with their own
 * token, so owners still see their drafts and walkers still see each other.
 */
public final class DiscoveryService {
    private static final int MAX_IDS = 100;
    private final PublicCatalogRepository catalog;
    private final PetRepository pets;
    private final UserRepository users;
    private final MediaRepository media;
    private final PetValidator validator;

    public DiscoveryService(PublicCatalogRepository catalog, PetRepository pets, UserRepository users,
                            MediaRepository media, PetValidator validator) {
        this.catalog = catalog;
        this.pets = pets;
        this.users = users;
        this.media = media;
        this.validator = validator;
    }

    public List<Pet> discover(AuthSession viewer, PetQuery query) {
        requireQuery(query);
        if (!query.city().isEmpty() && !Pet.SUPPORTED_CITIES.contains(query.city())) {
            throw new ValidationException("validation.city");
        }
        List<Pet> found = viewer == null ? catalog.published(query) : pets.findPublished(query, viewer);
        return found.stream().peek(validator::validate).collect(Collectors.toList());
    }

    public List<Pet> near(AuthSession viewer, double latitude, double longitude, int radiusKm, PetQuery query) {
        requireQuery(query);
        if (radiusKm < 1 || radiusKm > 100) {
            throw new ValidationException("validation.pet");
        }
        if (!ListingLocation.inArea(latitude, longitude)) {
            throw new ValidationException("location.out_of_area");
        }
        List<Pet> found = viewer == null
                ? catalog.publishedNear(latitude, longitude, radiusKm, query)
                : pets.findNear(latitude, longitude, radiusKm, query, viewer);
        return found.stream().peek(validator::validate).collect(Collectors.toList());
    }

    public Optional<Pet> listing(AuthSession viewer, UUID id) {
        return viewer == null ? catalog.publishedListing(id) : pets.findById(id, viewer);
    }

    /** Active listings among the given ids, e.g. saved favourites. Removed or hidden listings are skipped. */
    public List<Pet> listings(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_IDS) {
            throw new ValidationException("validation.pet");
        }
        return catalog.publishedListings(ids);
    }

    public List<Pet> byPublisher(AuthSession viewer, UUID ownerId) {
        return viewer == null ? catalog.publishedByOwner(ownerId) : pets.findPublishedByOwner(ownerId, viewer);
    }

    /** Publisher card. Guests never receive a phone number; contacting an owner needs an account. */
    public Optional<PublicProfile> publisher(AuthSession viewer, UUID userId) {
        return viewer == null ? catalog.publicProfile(userId) : users.publicProfile(viewer, userId);
    }

    public byte[] photo(AuthSession viewer, String path) {
        return viewer == null ? catalog.listingPhoto(path) : media.download(viewer, path);
    }

    private static void requireQuery(PetQuery query) {
        if (query == null || query.limit() < 1 || query.limit() > 50 || query.offset() < 0) {
            throw new ValidationException("validation.pet");
        }
    }
}
