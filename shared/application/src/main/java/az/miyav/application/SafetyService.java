package az.miyav.application;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.SafetyRepository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reporting members to moderators and blocking them. Blocked members' listings and chats are hidden from
 * the blocker; the server also refuses messages between them. The block list is cached per signed-in user.
 */
public final class SafetyService {
    private final SafetyRepository safety;
    private final Set<UUID> blocked = ConcurrentHashMap.newKeySet();
    private volatile UUID loadedFor;

    public SafetyService(SafetyRepository safety) {
        this.safety = safety;
    }

    public void report(AuthSession session, UUID reportedId, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.length() < 10 || text.length() > 1500) {
            throw new ValidationException("report.reason");
        }
        if (reportedId == null || reportedId.equals(session.userId())) {
            throw new ValidationException("report.self");
        }
        safety.report(session, reportedId, text);
    }

    public void block(AuthSession session, UUID userId) {
        if (userId == null || userId.equals(session.userId())) {
            throw new ValidationException("report.self");
        }
        safety.block(session, userId);
        blocked(session);
        blocked.add(userId);
    }

    public void unblock(AuthSession session, UUID userId) {
        safety.unblock(session, userId);
        blocked(session);
        blocked.remove(userId);
    }

    /** The members this user blocked; empty for guests. */
    public Set<UUID> blocked(AuthSession session) {
        if (session == null) {
            return Set.of();
        }
        if (!session.userId().equals(loadedFor)) {
            Set<UUID> fresh = safety.blocked(session);
            blocked.clear();
            blocked.addAll(fresh);
            loadedFor = session.userId();
        }
        return Set.copyOf(blocked);
    }

    public boolean isBlocked(AuthSession session, UUID userId) {
        return userId != null && blocked(session).contains(userId);
    }

    /** Drops items whose author the viewer blocked. A failed block-list read shows everything. */
    public <T> List<T> visible(AuthSession session, Collection<T> items, Function<T, UUID> author) {
        Set<UUID> hidden;
        try {
            hidden = blocked(session);
        } catch (RuntimeException exception) {
            hidden = Set.of();
        }
        if (hidden.isEmpty()) {
            return List.copyOf(items);
        }
        Set<UUID> skip = hidden;
        return items.stream().filter(item -> !skip.contains(author.apply(item))).collect(Collectors.toList());
    }

    /** Forgets the cached list, for sign-out and account switches. */
    public void clear() {
        blocked.clear();
        loadedFor = null;
    }
}
