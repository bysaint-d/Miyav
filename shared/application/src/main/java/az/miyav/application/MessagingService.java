package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.Conversation;
import az.miyav.domain.messaging.Message;
import az.miyav.domain.repository.MessageRepository;
import az.miyav.domain.walk.WalkListing;

import java.util.List;
import java.util.UUID;

/**
 * In-app chat, used to agree on a walk (time, place, price). Only signed-in users take part; a chat about
 * a walk listing is only possible between its owner and a walker who is interested in it, which the
 * database enforces as well.
 */
public final class MessagingService {
    private static final int HISTORY = 200;
    private final MessageRepository messages;

    public MessagingService(MessageRepository messages) {
        this.messages = messages;
    }

    public List<Conversation> inbox(AuthSession session) {
        requireSession(session);
        return messages.inbox(session);
    }

    public int unreadCount(AuthSession session) {
        return (int) inbox(session).stream().filter(Conversation::unread).count();
    }

    public List<Message> history(AuthSession session, UUID conversationId) {
        requireSession(session);
        return messages.messages(session, conversationId, HISTORY);
    }

    public Message send(AuthSession session, UUID conversationId, String text, UUID walkListingId) {
        requireSession(session);
        String body = text == null ? "" : text.trim();
        if (body.isEmpty() || body.length() > Message.MAX_LENGTH) {
            throw new ValidationException("chat.text");
        }
        return messages.send(session, conversationId, body, walkListingId);
    }

    /** Uploads the JPEG to the chat's private folder, then posts it with an optional caption. */
    public Message sendPhoto(AuthSession session, UUID conversationId, byte[] jpeg, String caption, UUID walkListingId) {
        requireSession(session);
        String text = caption == null ? "" : caption.trim();
        if (text.length() > Message.MAX_LENGTH) {
            throw new ValidationException("chat.text");
        }
        String path = messages.uploadPhoto(session, conversationId, jpeg);
        return messages.sendPhoto(session, conversationId, path, text, walkListingId);
    }

    public byte[] photo(AuthSession session, String imagePath) {
        requireSession(session);
        return messages.photo(session, imagePath);
    }

    /** The chat with the owner of an adoption listing; owners cannot open one with themselves. */
    public UUID openForAdoption(AuthSession session, az.miyav.domain.pet.Pet listing) {
        requireSession(session);
        if (listing.ownerId().equals(session.userId())) {
            throw new ValidationException("chat.own_listing");
        }
        return messages.openForAdoption(session, listing.id());
    }

    public void markRead(AuthSession session, UUID conversationId) {
        requireSession(session);
        messages.markRead(session, conversationId);
    }

    /**
     * The chat about a walk listing: a walker always talks to the owner; the owner must say which walker
     * (one who offered or was chosen).
     */
    public UUID openForWalk(AuthSession session, WalkListing listing, UUID walkerId) {
        requireSession(session);
        if (listing.ownedBy(session.userId())) {
            if (walkerId == null || walkerId.equals(session.userId())) {
                throw new ValidationException("walk.own_listing");
            }
            return messages.openForWalk(session, listing.id(), walkerId);
        }
        return messages.openForWalk(session, listing.id(), null);
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }
}
