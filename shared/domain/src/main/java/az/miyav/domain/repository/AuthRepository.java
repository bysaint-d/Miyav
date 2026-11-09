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

    /** Google authorize URL for the PKCE flow; the redirect then carries a one-time {@code code}. */
    default URI googleAuthorizeUri(String redirectTo, String codeChallenge) {
        return googleAuthorizeUri(redirectTo);
    }

    /** Whether an external sign-in provider such as {@code google} is enabled for this project. */
    default boolean providerEnabled(String provider) {
        return true;
    }

    /** Exchanges a PKCE authorization code. Only the device holding the verifier can complete it. */
    default AuthSession exchangeCode(String authCode, String codeVerifier) {
        throw new UnsupportedOperationException("exchangeCode");
    }

    /** Permanently deletes the signed-in user's account, data and files. */
    default void deleteAccount(AuthSession session) {
        throw new UnsupportedOperationException("deleteAccount");
    }
}
