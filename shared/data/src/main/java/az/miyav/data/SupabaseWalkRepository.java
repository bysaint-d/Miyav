package az.miyav.data;

import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.GeoPoint;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.WalkRepository;
import az.miyav.domain.walk.WalkHistoryEntry;
import az.miyav.domain.walk.WalkPet;
import az.miyav.domain.walk.WalkSession;
import az.miyav.domain.walk.Walker;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SupabaseWalkRepository implements WalkRepository {
    private static final String SESSION_SELECT = "id,status,pet_species,pet_name,status_message,started_at,expires_at";
    private static final Type SESSION_ROWS = new TypeToken<List<SessionRow>>() {
    }.getType();
    private static final Type WALKER_ROWS = new TypeToken<List<WalkerRow>>() {
    }.getType();
    private static final Type HISTORY_ROWS = new TypeToken<List<HistoryRow>>() {
    }.getType();
    private static final Type ROUTE_ROWS = new TypeToken<List<RouteRow>>() {
    }.getType();
    private static final Type PET_ROWS = new TypeToken<List<PetRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseWalkRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public WalkSession start(AuthSession session, PetSpecies species, String petName, String message, double latitude, double longitude) {
        JsonObject body = new JsonObject();
        body.addProperty("p_species", species.databaseValue());
        body.addProperty("p_pet_name", petName == null ? "" : petName);
        body.addProperty("p_message", message == null ? "" : message);
        body.addProperty("p_lat", latitude);
        body.addProperty("p_lng", longitude);
        return one(rpc(session, "start_walk", body));
    }

    @Override
    public WalkSession startWith(AuthSession session, List<UUID> petIds, UUID walkListingId, String message,
                                 double latitude, double longitude) {
        JsonObject body = new JsonObject();
        JsonArray pets = new JsonArray();
        petIds.forEach(id -> pets.add(id.toString()));
        body.add("p_pets", pets);
        if (walkListingId == null) {
            body.add("p_listing", JsonNull.INSTANCE);
        } else {
            body.addProperty("p_listing", walkListingId.toString());
        }
        body.addProperty("p_message", message == null ? "" : message);
        body.addProperty("p_lat", latitude);
        body.addProperty("p_lng", longitude);
        return one(rpc(session, "start_walk_with", body));
    }

    @Override
    public List<WalkPet> pets(AuthSession session, UUID sessionId) {
        JsonObject body = new JsonObject();
        body.addProperty("p_session", sessionId.toString());
        List<PetRow> rows = client.gson().fromJson(rpc(session, "walk_pets", body).body(), PET_ROWS);
        List<WalkPet> pets = new ArrayList<>();
        for (PetRow row : rows == null ? List.<PetRow>of() : rows) {
            pets.add(new WalkPet(row.name, PetSpecies.fromDatabase(row.species), row.breed, row.age, row.photo_path, row.from_job));
        }
        return pets;
    }

    @Override
    public WalkSession updateLocation(AuthSession session, double latitude, double longitude) {
        JsonObject body = new JsonObject();
        body.addProperty("p_lat", latitude);
        body.addProperty("p_lng", longitude);
        return one(rpc(session, "update_walk_location", body));
    }

    @Override
    public WalkSession setStatus(AuthSession session, WalkSession.Status status) {
        JsonObject body = new JsonObject();
        body.addProperty("p_status", status.databaseValue());
        return one(rpc(session, "set_walk_status", body));
    }

    @Override
    public Optional<WalkSession> current(AuthSession session) {
        HttpExchange response = client.send(
                "GET",
                "/rest/v1/walk_sessions?select=" + SESSION_SELECT
                        + "&user_id=eq." + session.userId() + "&status=neq.ended&order=started_at.desc&limit=1",
                session,
                null
        );
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<WalkSession> sessions = sessions(response.body());
        return sessions.stream().findFirst();
    }

    @Override
    public List<Walker> nearby(AuthSession session) {
        HttpExchange response = rpc(session, "nearby_walkers", new JsonObject());
        List<WalkerRow> rows = client.gson().fromJson(response.body(), WALKER_ROWS);
        List<Walker> walkers = new ArrayList<>();
        if (rows == null) {
            return walkers;
        }
        for (WalkerRow row : rows) {
            walkers.add(new Walker(
                    UUID.fromString(row.session_id),
                    UUID.fromString(row.user_id),
                    row.full_name,
                    row.avatar_path,
                    row.bio,
                    PetSpecies.fromDatabase(row.pet_species),
                    row.pet_name,
                    row.status_message,
                    row.latitude,
                    row.longitude,
                    row.distance_m,
                    row.started_at == null ? null : Timestamps.parse(row.started_at),
                    row.contact_phone
            ));
        }
        return walkers;
    }

    @Override
    public List<WalkHistoryEntry> history(AuthSession session, int limit, int offset) {
        JsonObject body = new JsonObject();
        body.addProperty("p_limit", limit);
        body.addProperty("p_offset", offset);
        List<HistoryRow> rows = client.gson().fromJson(rpc(session, "my_walks", body).body(), HISTORY_ROWS);
        List<WalkHistoryEntry> walks = new ArrayList<>();
        for (HistoryRow row : rows == null ? List.<HistoryRow>of() : rows) {
            PetSpecies species;
            try {
                species = row.pet_species == null ? null : PetSpecies.fromDatabase(row.pet_species);
            } catch (RuntimeException exception) {
                species = null;
            }
            walks.add(new WalkHistoryEntry(UUID.fromString(row.id), row.pet_name, species,
                    Timestamps.parse(row.started_at), row.ended_at == null ? null : Timestamps.parse(row.ended_at),
                    row.open, row.distance_m, row.points,
                    row.start_lat == null ? null : new GeoPoint(row.start_lat, row.start_lng),
                    row.end_lat == null ? null : new GeoPoint(row.end_lat, row.end_lng)));
        }
        return walks;
    }

    @Override
    public List<GeoPoint> route(AuthSession session, UUID sessionId) {
        JsonObject body = new JsonObject();
        body.addProperty("p_session", sessionId.toString());
        List<RouteRow> rows = client.gson().fromJson(rpc(session, "my_walk_route", body).body(), ROUTE_ROWS);
        List<GeoPoint> route = new ArrayList<>();
        for (RouteRow row : rows == null ? List.<RouteRow>of() : rows) {
            route.add(new GeoPoint(row.latitude, row.longitude));
        }
        return route;
    }

    @Override
    public void deleteFromHistory(AuthSession session, UUID sessionId) {
        JsonObject body = new JsonObject();
        body.addProperty("p_session", sessionId.toString());
        rpc(session, "delete_my_walk", body);
    }

    private HttpExchange rpc(AuthSession session, String function, JsonObject body) {
        HttpExchange response = client.send("POST", "/rest/v1/rpc/" + function, session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        return response;
    }

    private WalkSession one(HttpExchange response) {
        List<WalkSession> sessions = sessions(response.body());
        if (sessions.isEmpty()) {
            throw new az.miyav.common.error.ValidationException("walk.no_session");
        }
        return sessions.get(0);
    }

    private List<WalkSession> sessions(String json) {
        String body = json == null ? "" : json.trim();
        List<SessionRow> rows = new ArrayList<>();
        if (body.startsWith("{")) {
            SessionRow row = client.gson().fromJson(body, SessionRow.class);
            if (row != null && row.id != null) {
                rows.add(row);
            }
        } else if (!body.isEmpty()) {
            List<SessionRow> parsed = client.gson().fromJson(body, SESSION_ROWS);
            if (parsed != null) {
                rows.addAll(parsed);
            }
        }
        List<WalkSession> sessions = new ArrayList<>();
        for (SessionRow row : rows) {
            sessions.add(new WalkSession(
                    UUID.fromString(row.id),
                    WalkSession.Status.fromDatabase(row.status),
                    PetSpecies.fromDatabase(row.pet_species),
                    row.pet_name,
                    row.status_message,
                    Timestamps.parse(row.started_at),
                    row.expires_at == null ? null : Timestamps.parse(row.expires_at)
            ));
        }
        return sessions;
    }

    private static final class HistoryRow {
        String id;
        String pet_name;
        String pet_species;
        String started_at;
        String ended_at;
        boolean open;
        double distance_m;
        int points;
        Double start_lat;
        Double start_lng;
        Double end_lat;
        Double end_lng;
    }

    private static final class RouteRow {
        double latitude;
        double longitude;
    }

    private static final class SessionRow {
        String id;
        String status;
        String pet_species;
        String pet_name;
        String status_message;
        String started_at;
        String expires_at;
    }

    private static final class PetRow {
        String name;
        String species;
        String breed;
        java.math.BigDecimal age;
        String photo_path;
        boolean from_job;
    }

    private static final class WalkerRow {
        String session_id;
        String user_id;
        String full_name;
        String avatar_path;
        String bio;
        String pet_species;
        String pet_name;
        String status_message;
        double latitude;
        double longitude;
        int distance_m;
        String started_at;
        String contact_phone;
    }
}
