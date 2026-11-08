package az.miyav.domain.repository;

import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.user.PublicProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only queries that run with the anonymous role and no user token. Implementations may only reach
 * data that RLS exposes to {@code anon}: active listings, their photos, and public publisher cards.
 */
public interface PublicCatalogRepository {
    List<Pet> published(PetQuery query);

    List<Pet> publishedNear(double latitude, double longitude, int radiusKm, PetQuery query);

    Optional<Pet> publishedListing(UUID id);

    List<Pet> publishedListings(Collection<UUID> ids);

    List<Pet> publishedByOwner(UUID ownerId);

    Optional<PublicProfile> publicProfile(UUID userId);

    byte[] listingPhoto(String path);
}
