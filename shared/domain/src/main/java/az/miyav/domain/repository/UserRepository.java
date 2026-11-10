package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.user.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(AuthSession session);

    User updateProfile(AuthSession session, User user);

    List<User> listDirectory(AuthSession session);

    default Optional<PublicProfile> publicProfile(AuthSession session, UUID userId) {
        return Optional.empty();
    }

    /** The Terms version this member accepted; empty when they never did. */
    default Optional<String> acceptedTerms(AuthSession session) {
        return Optional.empty();
    }

    default void acceptTerms(AuthSession session, String version) {
        throw new UnsupportedOperationException("acceptTerms");
    }
}
