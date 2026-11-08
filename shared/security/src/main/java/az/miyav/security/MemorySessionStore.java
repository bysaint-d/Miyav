package az.miyav.security;

import az.miyav.domain.auth.AuthSession;

import java.util.Optional;

public final class MemorySessionStore implements SessionStore {
    private AuthSession session;

    @Override
    public void save(AuthSession session) {
        this.session = session;
    }

    @Override
    public Optional<AuthSession> load() {
        return Optional.ofNullable(session);
    }

    @Override
    public void clear() {
        session = null;
    }
}
