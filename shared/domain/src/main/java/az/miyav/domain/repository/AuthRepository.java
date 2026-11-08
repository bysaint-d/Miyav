package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;

import java.net.URI;

public interface AuthRepository {
    AuthSession signUp(String email, String password, String fullName);

    default AuthSession signUp(String email, String password, String fullName, String redirectTo) {
        return signUp(email, password, fullName);
    }

    AuthSession signIn(String email, String password);

    AuthSession refresh(String refreshToken);

    void signOut(AuthSession session);

    void requestPasswordReset(String email);

    default void requestPasswordReset(String email, String redirectTo) {
        requestPasswordReset(email);
    }

    default AuthSession fromTokens(String accessToken, String refreshToken, long expiresInSeconds) {
        throw new UnsupportedOperationException("fromTokens");
    }

    default void updatePassword(AuthSession session, String newPassword) {
        throw new UnsupportedOperationException("updatePassword");
    }

    URI googleAuthorizeUri(String redirectTo);
}
