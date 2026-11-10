package az.miyav.data;

import az.miyav.common.error.PersistenceException;
import az.miyav.common.error.ValidationException;
import az.miyav.common.time.SystemTimeSource;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.AuthRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SupabaseAuthRepository implements AuthRepository {
    private final SupabaseConfig config;
    private final SupabaseClient client;

    public SupabaseAuthRepository(SupabaseConfig config, SupabaseClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public AuthSession signUp(String email, String password, String fullName) {
        return signUp(email, password, fullName, null);
    }

    @Override
    public AuthSession signUp(String email, String password, String fullName, String redirectTo) {
        return signUp(email, password, fullName, redirectTo, null);
    }

    @Override
    public AuthSession signUp(String email, String password, String fullName, String redirectTo, String termsVersion) {
        JsonObject data = new JsonObject();
        data.addProperty("full_name", fullName);
        if (termsVersion != null) {
            data.addProperty("terms_version", termsVersion);
        }
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.add("data", data);
        HttpExchange response = client.send("POST", "/auth/v1/signup" + redirect(redirectTo), null, body.toString());
        return sessionFrom(response);
    }

    @Override
    public AuthSession signIn(String email, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        HttpExchange response = client.send("POST", "/auth/v1/token?grant_type=password", null, body.toString());
        return sessionFrom(response);
    }

    @Override
    public AuthSession refresh(String refreshToken) {
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);
        HttpExchange response = client.send("POST", "/auth/v1/token?grant_type=refresh_token", null, body.toString());
        return sessionFrom(response);
    }

    @Override
    public void signOut(AuthSession session) {
        HttpExchange response = client.send("POST", "/auth/v1/logout", session, "{}");
        if (response.status() == 401 || response.status() / 100 == 2) {
            return;
        }
        throw new PersistenceException("auth.unavailable");
    }

    @Override
    public void requestPasswordReset(String email) {
        requestPasswordReset(email, null);
    }

    @Override
    public void requestPasswordReset(String email, String redirectTo) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        HttpExchange response = client.send("POST", "/auth/v1/recover" + redirect(redirectTo), null, body.toString());
        if (response.status() / 100 != 2) {
            throw failure(response);
        }
    }

    @Override
    public AuthSession fromTokens(String accessToken, String refreshToken, long expiresInSeconds) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new PersistenceException("auth.link_invalid");
        }
        AuthSession probe = new AuthSession(
                new UUID(0, 0), "", accessToken, refreshToken, new SystemTimeSource().now().plusSeconds(60), null);
        HttpExchange response = client.send("GET", "/auth/v1/user", probe, null);
        if (response.status() == 401 || response.status() == 403) {
            throw new PersistenceException("auth.link_invalid");
        }
        if (response.status() / 100 != 2) {
            throw new PersistenceException("auth.unavailable");
        }
        TokenUser user = client.gson().fromJson(response.body(), TokenUser.class);
        if (user == null || user.id == null) {
            throw new PersistenceException("auth.link_invalid");
        }
        long seconds = expiresInSeconds > 0 ? expiresInSeconds : 3600;
        return new AuthSession(
                UUID.fromString(user.id),
                user.email,
                accessToken,
                refreshToken,
                new SystemTimeSource().now().plusSeconds(seconds),
                null
        );
    }

    @Override
    public void updatePassword(AuthSession session, String newPassword) {
        JsonObject body = new JsonObject();
        body.addProperty("password", newPassword);
        HttpExchange response = client.send("PUT", "/auth/v1/user", session, body.toString());
        if (response.status() / 100 != 2) {
            String code = errorCode(response);
            if ("same_password".equals(code)) {
                throw new ValidationException("auth.same_password");
            }
            if ("weak_password".equals(code)) {
                throw new ValidationException("validation.password");
            }
            if (response.status() == 401) {
                throw new PersistenceException("auth.session_expired");
            }
            throw new PersistenceException("auth.unavailable");
        }
    }

    @Override
    public URI googleAuthorizeUri(String redirectTo) {
        config.requireConfigured();
        String encoded = URLEncoder.encode(redirectTo, StandardCharsets.UTF_8);
        return config.endpoint("/auth/v1/authorize?provider=google&redirect_to=" + encoded);
    }

    @Override
    public URI googleAuthorizeUri(String redirectTo, String codeChallenge) {
        config.requireConfigured();
        String encoded = URLEncoder.encode(redirectTo, StandardCharsets.UTF_8);
        return config.endpoint("/auth/v1/authorize?provider=google&redirect_to=" + encoded
                + "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
                + "&code_challenge_method=s256");
    }

    @Override
    public boolean providerEnabled(String provider) {
        HttpExchange response = client.send("GET", "/auth/v1/settings", null, null);
        if (response.status() / 100 != 2) {
            throw new PersistenceException("auth.unavailable");
        }
        try {
            JsonObject external = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("external");
            return external != null && external.has(provider) && external.get(provider).getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public AuthSession exchangeCode(String authCode, String codeVerifier) {
        if (authCode == null || authCode.isBlank() || codeVerifier == null || codeVerifier.isBlank()) {
            throw new PersistenceException("auth.link_invalid");
        }
        JsonObject body = new JsonObject();
        body.addProperty("auth_code", authCode);
        body.addProperty("code_verifier", codeVerifier);
        HttpExchange response = client.send("POST", "/auth/v1/token?grant_type=pkce", null, body.toString());
        if (response.status() == 400 || response.status() == 401 || response.status() == 404) {
            throw new PersistenceException("auth.link_invalid");
        }
        return sessionFrom(response);
    }

    @Override
    public void deleteAccount(AuthSession session) {
        HttpExchange response = client.send("POST", "/functions/v1/delete-account", session, "{}");
        if (response.status() / 100 == 2) {
            return;
        }
        if (response.status() == 401) {
            throw new PersistenceException("auth.session_expired");
        }
        if (response.status() == 403) {
            throw new PersistenceException("account.admin");
        }
        throw new PersistenceException("account.delete_failed");
    }

    private AuthSession sessionFrom(HttpExchange response) {
        if (response.status() / 100 != 2) {
            throw failure(response);
        }
        TokenPayload token = client.gson().fromJson(response.body(), TokenPayload.class);
        if (token == null || token.access_token == null || token.access_token.isBlank() || token.user == null || token.user.id == null) {
            throw new PersistenceException("auth.confirmation_required");
        }
        long seconds = token.expires_in > 0 ? token.expires_in : 3600;
        return new AuthSession(
                UUID.fromString(token.user.id),
                token.user.email,
                token.access_token,
                token.refresh_token,
                new SystemTimeSource().now().plusSeconds(seconds),
                null
        );
    }

    static RuntimeException failure(HttpExchange response) {
        String code = errorCode(response);
        switch (code) {
            case "email_not_confirmed":
                return new PersistenceException("auth.confirmation_required");
            case "user_already_exists":
            case "email_exists":
                return new ValidationException("auth.user_exists");
            case "weak_password":
                return new ValidationException("validation.password");
            case "email_address_invalid":
            case "validation_failed":
                return new ValidationException("validation.email");
            case "over_email_send_rate_limit":
            case "over_request_rate_limit":
                return new PersistenceException("auth.rate_limited");
            case "signup_disabled":
                return new PersistenceException("auth.unavailable");
            default:
                break;
        }
        if (response.status() == 429) {
            return new PersistenceException("auth.rate_limited");
        }
        if (response.status() == 400 || response.status() == 401 || response.status() == 422) {
            return new PersistenceException("auth.failed");
        }
        return new PersistenceException("auth.unavailable");
    }

    private static String errorCode(HttpExchange response) {
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("error_code") && json.get("error_code").isJsonPrimitive()) {
                return json.get("error_code").getAsString();
            }
            if (json.has("error") && json.get("error").isJsonPrimitive()) {
                return json.get("error").getAsString();
            }
        } catch (RuntimeException ignored) {
            return "";
        }
        return "";
    }

    private static String redirect(String redirectTo) {
        if (redirectTo == null || redirectTo.isBlank()) {
            return "";
        }
        return "?redirect_to=" + URLEncoder.encode(redirectTo, StandardCharsets.UTF_8);
    }

    private static final class TokenPayload {
        String access_token;
        String refresh_token;
        long expires_in;
        TokenUser user;
    }

    private static final class TokenUser {
        String id;
        String email;
    }
}
