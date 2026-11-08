package az.miyav.domain.auth;

import az.miyav.domain.user.UserRole;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuthSession {
    private final UUID userId;
    private final String email;
    private final String accessToken;
    private final String refreshToken;
    private final Instant expiresAt;
    private final UserRole role;

    public AuthSession(
            UUID userId,
            String email,
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            UserRole role
    ) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.email = email == null ? "" : email;
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken");
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.role = role;
    }

    public UUID userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public UserRole role() {
        return role;
    }

    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public AuthSession withRole(UserRole role) {
        return new AuthSession(userId, email, accessToken, refreshToken, expiresAt, role);
    }

    public AuthSession withTokens(String accessToken, String refreshToken, Instant expiresAt) {
        return new AuthSession(userId, email, accessToken, refreshToken, expiresAt, role);
    }

    public boolean expiringAt(Instant when) {
        return !expiresAt.isAfter(when);
    }

    @Override
    public String toString() {
        return "AuthSession{userId=" + userId + ", role=" + role + ", expiresAt=" + expiresAt + "}";
    }
}
