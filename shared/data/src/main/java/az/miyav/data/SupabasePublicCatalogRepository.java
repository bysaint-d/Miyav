package az.miyav.data;

import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.repository.PublicCatalogRepository;
import az.miyav.domain.user.PublicProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public catalog served with the publishable key only: no user token is ever attached, so every query
 * runs as {@code anon} and sees exactly what RLS grants that role.
 */
public final class SupabasePublicCatalogRepository implements PublicCatalogRepository {
    private final SupabasePetRepository pets;
    private final SupabaseUserRepository users;
    private final SupabaseMediaRepository media;

    public SupabasePublicCatalogRepository(SupabaseClient client) {
        this(new SupabasePetRepository(client), new SupabaseUserRepository(client), new SupabaseMediaRepository(client));
    }

    SupabasePublicCatalogRepository(SupabasePetRepository pets, SupabaseUserRepository users, SupabaseMediaRepository media) {
        this.pets = pets;
        this.users = users;
        this.media = media;
    }

    @Override
    public List<Pet> published(PetQuery query) {
        return pets.findPublished(query, null);
    }

    @Override
    public List<Pet> publishedNear(double latitude, double longitude, int radiusKm, PetQuery query) {
        return pets.findNear(latitude, longitude, radiusKm, query, null);
    }

    @Override
    public Optional<Pet> publishedListing(UUID id) {
        return pets.findById(id, null).filter(pet -> pet.status() == PetStatus.PUBLISHED);
    }

    @Override
    public List<Pet> publishedListings(Collection<UUID> ids) {
        return pets.findPublishedByIds(ids, null);
    }

    @Override
    public List<Pet> publishedByOwner(UUID ownerId) {
        return pets.findPublishedByOwner(ownerId, null);
    }

    @Override
    public Optional<PublicProfile> publicProfile(UUID userId) {
        return users.publicProfile(null, userId);
    }

    @Override
    public byte[] listingPhoto(String path) {
        return media.download(null, path);
    }
}
