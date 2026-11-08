package az.miyav.application;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Dog;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.repository.MediaRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.repository.PublicCatalogRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.validation.PetValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscoveryServiceTest {
    private final PublicCatalogRepository catalog = mock(PublicCatalogRepository.class);
    private final PetRepository pets = mock(PetRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MediaRepository media = mock(MediaRepository.class);
    private final DiscoveryService discovery = new DiscoveryService(catalog, pets, users, media, new PetValidator());

    @Test
    void guestBrowsesPublicListingsThroughTheAnonymousCatalogOnly() {
        Pet dog = dog(PetStatus.PUBLISHED);
        when(catalog.published(PetQuery.firstPage())).thenReturn(List.of(dog));

        assertEquals(List.of(dog), discovery.discover(null, PetQuery.firstPage()));
        verifyNoInteractions(pets, users, media);
    }

    @Test
    void guestCanSearchAndFilter() {
        PetQuery query = new PetQuery(PetSpecies.DOG, "Bakı", "labrador", 20, 0);
        when(catalog.published(query)).thenReturn(List.of());

        discovery.discover(null, query);
        verify(catalog).published(query);

        PetQuery near = new PetQuery(PetSpecies.CAT, "", "", 20, 0);
        discovery.near(null, 40.4, 49.8, 25, near);
        verify(catalog).publishedNear(40.4, 49.8, 25, near);
        verifyNoInteractions(pets);
    }

    @Test
    void invalidFiltersAreRejectedBeforeAnyQuery() {
        assertThrows(ValidationException.class, () -> discovery.discover(null, new PetQuery(null, "Gəncə", "", 20, 0)));
        assertThrows(ValidationException.class, () -> discovery.discover(null, new PetQuery(null, "", "", 500, 0)));
        assertThrows(ValidationException.class, () -> discovery.near(null, 10, 10, 25, PetQuery.firstPage()));
        verifyNoInteractions(catalog);
    }

    @Test
    void guestCanViewPetDetailsPublisherAndPhotos() {
        Pet dog = dog(PetStatus.PUBLISHED);
        PublicProfile publisher = new PublicProfile(dog.ownerId(), "Aysel", null, "Bakı", "", false, null, Instant.EPOCH);
        when(catalog.publishedListing(dog.id())).thenReturn(Optional.of(dog));
        when(catalog.publicProfile(dog.ownerId())).thenReturn(Optional.of(publisher));
        when(catalog.listingPhoto("a/b/c.jpg")).thenReturn(new byte[]{1});

        assertEquals(Optional.of(dog), discovery.listing(null, dog.id()));
        assertTrue(discovery.publisher(null, dog.ownerId()).orElseThrow().contactDigits().isEmpty(),
                "guests never get a phone number");
        assertEquals(1, discovery.photo(null, "a/b/c.jpg").length);
        verifyNoInteractions(pets, users, media);
    }

    @Test
    void signedInViewerReadsWithTheirOwnSession() {
        AuthSession session = new AuthSession(UUID.randomUUID(), "a@example.com", "token", "r", Instant.MAX, UserRole.OWNER);
        when(pets.findPublished(any(), any())).thenReturn(List.of());

        discovery.discover(session, PetQuery.firstPage());
        discovery.listing(session, UUID.randomUUID());

        verify(pets).findPublished(PetQuery.firstPage(), session);
        verify(pets).findById(any(), any());
        verifyNoInteractions(catalog);
    }

    static Pet dog(PetStatus status) {
        return new Dog(UUID.randomUUID(), UUID.randomUUID(), "Boncuk", "mix", 12, PetGender.FEMALE, "Bakı",
                "Sakit və mehriban bir itdir.", status, List.of("photo.jpg"));
    }
}
