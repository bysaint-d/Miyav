package az.miyav.security;

import az.miyav.domain.auth.AuthSession;

import java.util.Optional;

public interface SessionStore {
    void save(AuthSession session);

    Optional<AuthSession> load();

    void clear();
}
