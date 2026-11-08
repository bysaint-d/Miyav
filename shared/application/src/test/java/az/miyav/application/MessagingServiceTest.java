package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.Conversation;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.MessageRepository;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkListingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessagingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private final MessageRepository repository = mock(MessageRepository.class);
    private final MessagingService service = new MessagingService(repository);
    private final AuthSession owner = session();
    private final AuthSession walker = session();
    private final WalkListing listing = new WalkListing(UUID.randomUUID(), owner.userId(),
            new WalkListingDetails("Boncuk", PetSpecies.DOG, NOW.plusSeconds(3600), 60, BigDecimal.TEN, "Bakı", null, ""),
            WalkListingStatus.OPEN, null, NOW);

    @Test
    void guestsCannotChat() {
        UUID room = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> service.inbox(null));
        assertThrows(AccessDeniedException.class, () -> service.send(null, room, "Salam", null));
        assertThrows(AccessDeniedException.class, () -> service.openForWalk(null, listing, null));
        verifyNoInteractions(repository);
    }

    @Test
    void emptyOrHugeMessagesAreRejected() {
        UUID room = UUID.randomUUID();
        assertEquals("chat.text", assertThrows(ValidationException.class, () -> service.send(walker, room, "   ", null)).code());
        assertEquals("chat.text", assertThrows(ValidationException.class, () -> service.send(walker, room, "a".repeat(2001), null)).code());
        verify(repository, never()).send(any(), any(), anyString(), any());
        service.send(walker, room, "  20 manat olar?  ", listing.id());
        verify(repository).send(walker, room, "20 manat olar?", listing.id());
    }

    @Test
    void walkersTalkToTheOwnerAndTheOwnerPicksTheWalker() {
        service.openForWalk(walker, listing, UUID.randomUUID());
        verify(repository).openForWalk(walker, listing.id(), null);

        assertThrows(ValidationException.class, () -> service.openForWalk(owner, listing, null));
        service.openForWalk(owner, listing, walker.userId());
        verify(repository).openForWalk(owner, listing.id(), walker.userId());
    }

    @Test
    void ownMessagesNeverCountAsUnread() {
        Conversation mine = new Conversation(UUID.randomUUID(), UUID.randomUUID(), "Aysel", NOW, "Salam", true, null, true);
        Conversation theirs = new Conversation(UUID.randomUUID(), UUID.randomUUID(), "Murad", NOW, "", false, BigDecimal.TEN, true);
        assertFalse(mine.unread());
        when(repository.inbox(walker)).thenReturn(List.of(mine, theirs));
        assertEquals(1, service.unreadCount(walker));
    }

    private static AuthSession session() {
        return new AuthSession(UUID.randomUUID(), "a@example.com", "t", "r", Instant.MAX, UserRole.OWNER);
    }
}
