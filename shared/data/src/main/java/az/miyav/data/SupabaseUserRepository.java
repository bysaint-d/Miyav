package az.miyav.data;

import az.miyav.common.error.PersistenceException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SupabaseUserRepository implements UserRepository {
    private static final String SELECT = "id,full_name,city,district,phone,role,suspended,preferred_language,avatar_path,bio,phone_public";
    private static final Type ROWS = new TypeToken<List<ProfileRow>>() {
    }.getType();
    private static final Type PUBLIC_ROWS = new TypeToken<List<PublicRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseUserRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public Optional<User> findById(AuthSession session) {
        HttpExchange response = client.send(
                "GET",
                "/rest/v1/profiles?id=eq." + session.userId() + "&select=" + SELECT,
                session,
                null
        );
        if (response.status() / 100 != 2) {
            throw new PersistenceException("auth.profile_unavailable");
        }
        List<ProfileRow> rows = client.gson().fromJson(response.body(), ROWS);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toUser(rows.get(0), session.email()));
    }

    @Override
    public User updateProfile(AuthSession session, User user) {
        JsonObject body = new JsonObject();
        body.addProperty("full_name", user.fullName());
        body.addProperty("city", user.city());
        body.addProperty("district", user.district());
        body.addProperty("phone", user.phone());
        body.addProperty("preferred_language", user.preferredLocale().tag());
        body.addProperty("bio", user.bio());
        body.addProperty("phone_public", user.phonePublic());
        if (user.avatarPath() == null) {
            body.add("avatar_path", JsonNull.INSTANCE);
        } else {
            body.addProperty("avatar_path", user.avatarPath());
        }
        HttpExchange response = client.send(
                "PATCH",
                "/rest/v1/profiles?id=eq." + session.userId() + "&select=" + SELECT,
                session,
                body.toString(),
                Map.of("Prefer", "return=representation")
        );
        if (response.status() / 100 != 2) {
            throw new PersistenceException("auth.profile_unavailable");
        }
        List<ProfileRow> rows = client.gson().fromJson(response.body(), ROWS);
        if (rows == null || rows.isEmpty()) {
            throw new PersistenceException("auth.profile_unavailable");
        }
        return toUser(rows.get(0), session.email());
    }

    @Override
    public List<User> listDirectory(AuthSession session) {
        HttpExchange response = client.send(
                "GET",
                "/rest/v1/profiles?select=" + SELECT + "&order=created_at.desc&limit=200",
                session,
                null
        );
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<ProfileRow> rows = client.gson().fromJson(response.body(), ROWS);
        List<User> users = new ArrayList<>();
        if (rows == null) {
            return users;
        }
        for (ProfileRow row : rows) {
            users.add(toUser(row, ""));
        }
        return users;
    }

    @Override
    public Optional<PublicProfile> publicProfile(AuthSession session, UUID userId) {
        JsonObject body = new JsonObject();
        body.addProperty("p_user", userId.toString());
        HttpExchange response = client.send("POST", "/rest/v1/rpc/public_profile", session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<PublicRow> rows = client.gson().fromJson(response.body(), PUBLIC_ROWS);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        PublicRow row = rows.get(0);
        return Optional.of(new PublicProfile(
                UUID.fromString(row.id),
                row.full_name,
                row.avatar_path,
                row.city,
                row.bio,
                row.verified,
                row.contact_phone,
                row.member_since == null ? null : Timestamps.parse(row.member_since)
        ));
    }

    private static User toUser(ProfileRow row, String email) {
        return new User(
                UUID.fromString(row.id),
                email,
                row.full_name,
                UserRole.fromDatabase(row.role),
                row.suspended,
                row.city,
                row.district,
                row.phone,
                LocaleCode.fromTag(row.preferred_language),
                row.avatar_path,
                row.bio,
                row.phone_public
        );
    }

    private static final class ProfileRow {
        String id;
        String full_name;
        String city;
        String district;
        String phone;
        String role;
        boolean suspended;
        String preferred_language;
        String avatar_path;
        String bio;
        boolean phone_public;
    }

    private static final class PublicRow {
        String id;
        String full_name;
        String avatar_path;
        String city;
        String bio;
        boolean verified;
        String contact_phone;
        String member_since;
    }
}
