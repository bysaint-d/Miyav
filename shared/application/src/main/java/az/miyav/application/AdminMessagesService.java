package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.AdminConversation;
import az.miyav.domain.messaging.Message;
import az.miyav.domain.repository.AdminMessageRepository;
import az.miyav.security.RoleGuard;

import java.util.List;
import java.util.UUID;

/** Message moderation for administrators. The server checks the role again and audits every read. */
public final class AdminMessagesService {
    private final AdminMessageRepository messages;
    private final RoleGuard access;

    public AdminMessagesService(AdminMessageRepository messages, RoleGuard access) {
        this.messages = messages;
        this.access = access;
    }

    public List<AdminConversation> conversations(AuthSession session, String search) {
        requireAdmin(session);
        return messages.conversations(session, search);
    }

    public List<Message> read(AuthSession session, UUID conversationId) {
        requireAdmin(session);
        return messages.messages(session, conversationId);
    }

    public byte[] photo(AuthSession session, Message message) {
        requireAdmin(session);
        return messages.photo(session, message.imagePath().orElseThrow());
    }

    private void requireAdmin(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("access.admin");
        }
        access.requireAdmin(session.role());
    }
}
