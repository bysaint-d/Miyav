package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.admin.ActivityItem;
import az.miyav.domain.admin.CityActivity;
import az.miyav.domain.admin.DailyCount;
import az.miyav.domain.admin.PlatformOverview;
import az.miyav.domain.admin.UserSummary;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.GeoPoint;
import az.miyav.domain.map.MapPoint;
import az.miyav.domain.map.MapPointKind;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.AdminInsightsRepository;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.walk.CareService;
import az.miyav.domain.walk.WalkActivity;
import az.miyav.domain.walk.WalkRequest;
import az.miyav.domain.walk.WalkSession;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Calls the {@code admin_*} RPCs. The server rejects non-administrators with FORBIDDEN. */
public final class SupabaseAdminInsightsRepository implements AdminInsightsRepository {
    private static final Type REQUEST_ROWS = new TypeToken<List<RequestRow>>() {
    }.getType();
    private static final Type WALK_ROWS = new TypeToken<List<WalkRow>>() {
    }.getType();
    private static final Type POINT_ROWS = new TypeToken<List<PointRow>>() {
    }.getType();
    private static final Type USER_ROWS = new TypeToken<List<UserRow>>() {
    }.getType();
    private static final Type PET_ROWS = new TypeToken<List<PetRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseAdminInsightsRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public PlatformOverview overview(AuthSession session) {
        JsonObject json = JsonParser.parseString(call(session, "admin_overview", "{}")).getAsJsonObject();
        PlatformOverview.Counts counts = new PlatformOverview.Counts(
                integer(json, "users_total"), integer(json, "users_suspended"), integer(json, "users_new_7d"),
                integer(json, "users_active_30d"), integer(json, "pets_total"), integer(json, "helpers_active"),
                integer(json, "helpers_verified"), integer(json, "requests_upcoming"), integer(json, "walks_total"),
                integer(json, "walks_live"), integer(json, "applications_pending"), integer(json, "reports_open"),
                integer(json, "conversations_total"), integer(json, "messages_7d"), integer(json, "notifications_total"),
                integer(json, "notifications_unread"));
        List<CityActivity> cities = new ArrayList<>();
        for (JsonElement element : array(json, "cities")) {
            JsonObject city = element.getAsJsonObject();
            cities.add(new CityActivity(text(city, "city"), integer(city, "users"), integer(city, "listings"), integer(city, "helpers")));
        }
        List<ActivityItem> recentUsers = new ArrayList<>();
        for (JsonElement element : array(json, "recent_users")) {
            JsonObject user = element.getAsJsonObject();
            recentUsers.add(new ActivityItem(ActivityItem.Kind.REGISTRATION, uuid(user, "id"), text(user, "full_name"),
                    text(user, "city"), text(user, "role"), instant(user, "created_at")));
        }
        List<ActivityItem> recentWalks = new ArrayList<>();
        for (JsonElement element : array(json, "recent_walks")) {
            JsonObject walk = element.getAsJsonObject();
            recentWalks.add(new ActivityItem(ActivityItem.Kind.WALK, uuid(walk, "id"), text(walk, "full_name"),
                    text(walk, "pet_name") + " · " + text(walk, "pet_species"), text(walk, "status"), instant(walk, "started_at")));
        }
        List<ActivityItem> recentListings = new ArrayList<>();
        for (JsonElement element : array(json, "recent_listings")) {
            JsonObject listing = element.getAsJsonObject();
            recentListings.add(new ActivityItem(ActivityItem.Kind.ADOPTION, uuid(listing, "id"), text(listing, "name"),
                    text(listing, "species") + " · " + text(listing, "city"), text(listing, "status"), instant(listing, "updated_at")));
        }
        return new PlatformOverview(counts, counts(json, "requests_by_status"), counts(json, "listings_by_status"),
                series(json, "registrations_14d"), series(json, "walks_14d"), cities, recentUsers, recentWalks, recentListings);
    }

    @Override
    public List<WalkRequest> walkRequests(AuthSession session, String storedStatus) {
        JsonObject body = new JsonObject();
        body.addProperty("p_status", storedStatus);
        List<RequestRow> rows = client.gson().fromJson(call(session, "admin_walk_requests", body.toString()), REQUEST_ROWS);
        List<WalkRequest> requests = new ArrayList<>();
        for (RequestRow row : rows == null ? List.<RequestRow>of() : rows) {
            requests.add(new WalkRequest(
                    UUID.fromString(row.id),
                    new WalkRequest.Participant(UUID.fromString(row.owner_id), row.owner_name),
                    new WalkRequest.Participant(UUID.fromString(row.helper_id), row.helper_name),
                    row.pet_name, row.pet_type, CareService.fromDatabase(row.service_kind),
                    Timestamps.parse(row.starts_at), Timestamps.parse(row.ends_at), row.status,
                    row.total_price, row.price_unit, row.city, row.district, row.notes,
                    row.created_at == null ? null : Timestamps.parse(row.created_at)));
        }
        return requests;
    }

    @Override
    public List<WalkActivity> walkActivity(AuthSession session) {
        List<WalkRow> rows = client.gson().fromJson(call(session, "admin_walk_sessions", "{}"), WALK_ROWS);
        List<WalkActivity> walks = new ArrayList<>();
        for (WalkRow row : rows == null ? List.<WalkRow>of() : rows) {
            GeoPoint position = row.latitude == null || row.longitude == null ? null : new GeoPoint(row.latitude, row.longitude);
            walks.add(new WalkActivity(UUID.fromString(row.id), UUID.fromString(row.user_id), row.user_name, row.pet_name,
                    species(row.pet_species), WalkSession.Status.fromDatabase(row.status), row.status_message,
                    Timestamps.parse(row.started_at), row.ended_at == null ? null : Timestamps.parse(row.ended_at), position));
        }
        return walks;
    }

    @Override
    public List<MapPoint> mapPoints(AuthSession session) {
        List<PointRow> rows = client.gson().fromJson(call(session, "admin_map_points", "{}"), POINT_ROWS);
        List<MapPoint> points = new ArrayList<>();
        for (PointRow row : rows == null ? List.<PointRow>of() : rows) {
            if (row.latitude == null || row.longitude == null) {
                continue;
            }
            points.add(MapPoint.of(MapPointKind.fromWire(row.kind), UUID.fromString(row.id), row.title, row.subtitle,
                    new GeoPoint(row.latitude, row.longitude), row.verified));
        }
        return points;
    }

    @Override
    public List<UserSummary> users(AuthSession session) {
        List<UserRow> rows = client.gson().fromJson(call(session, "admin_users", "{}"), USER_ROWS);
        List<UserSummary> users = new ArrayList<>();
        for (UserRow row : rows == null ? List.<UserRow>of() : rows) {
            users.add(new UserSummary(UUID.fromString(row.id), row.full_name, row.city, row.district,
                    UserRole.fromDatabase(row.role), row.suspended, row.avatar_path, Timestamps.parse(row.created_at),
                    row.pets_count, row.listings_count, row.walks_count, row.requests_count,
                    row.last_active_at == null ? null : Timestamps.parse(row.last_active_at)));
        }
        return users;
    }

    @Override
    public List<PetProfile> pets(AuthSession session) {
        List<PetRow> rows = client.gson().fromJson(call(session, "admin_pets", "{}"), PET_ROWS);
        List<PetProfile> pets = new ArrayList<>();
        for (PetRow row : rows == null ? List.<PetRow>of() : rows) {
            pets.add(new PetProfile(UUID.fromString(row.id), UUID.fromString(row.owner_id), row.owner_name, row.name,
                    species(row.animal_type), row.breed, row.age, row.weight, row.photo_path,
                    row.created_at == null ? null : Timestamps.parse(row.created_at)));
        }
        return pets;
    }

    private String call(AuthSession session, String function, String body) {
        if (session == null) {
            throw new AccessDeniedException("access.admin");
        }
        HttpExchange response = client.send("POST", "/rest/v1/rpc/" + URLEncoder.encode(function, StandardCharsets.UTF_8), session, body);
        if (response.status() / 100 != 2) {
            RuntimeException mapped = PostgrestErrors.from(response);
            if (mapped instanceof AccessDeniedException) {
                throw new AccessDeniedException("access.admin");
            }
            throw mapped;
        }
        return response.body();
    }

    private static PetSpecies species(String value) {
        try {
            return PetSpecies.fromDatabase(value);
        } catch (IllegalArgumentException exception) {
            return PetSpecies.OTHER;
        }
    }

    private static Map<String, Integer> counts(JsonObject json, String name) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (json.has(name) && json.get(name).isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(name).entrySet()) {
                counts.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
        return counts;
    }

    private static List<DailyCount> series(JsonObject json, String name) {
        List<DailyCount> series = new ArrayList<>();
        for (JsonElement element : array(json, name)) {
            JsonObject day = element.getAsJsonObject();
            series.add(new DailyCount(LocalDate.parse(text(day, "day")), integer(day, "count")));
        }
        return series;
    }

    private static JsonArray array(JsonObject json, String name) {
        return json.has(name) && json.get(name).isJsonArray() ? json.getAsJsonArray(name) : new JsonArray();
    }

    private static int integer(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsInt() : 0;
    }

    private static String text(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : "";
    }

    private static UUID uuid(JsonObject json, String name) {
        return UUID.fromString(text(json, name));
    }

    private static Instant instant(JsonObject json, String name) {
        String value = text(json, name);
        return value.isEmpty() ? null : Timestamps.parse(value);
    }

    private static final class RequestRow {
        String id;
        String owner_id;
        String owner_name;
        String helper_id;
        String helper_name;
        String pet_name;
        String pet_type;
        String service_kind;
        String starts_at;
        String ends_at;
        String status;
        BigDecimal total_price;
        String price_unit;
        String city;
        String district;
        String notes;
        String created_at;
    }

    private static final class WalkRow {
        String id;
        String user_id;
        String user_name;
        String pet_name;
        String pet_species;
        String status;
        String status_message;
        String started_at;
        String ended_at;
        Double latitude;
        Double longitude;
    }

    private static final class PointRow {
        String kind;
        String id;
        String title;
        String subtitle;
        Double latitude;
        Double longitude;
        boolean verified;
    }

    private static final class UserRow {
        String id;
        String full_name;
        String city;
        String district;
        String role;
        boolean suspended;
        String avatar_path;
        String created_at;
        int pets_count;
        int listings_count;
        int walks_count;
        int requests_count;
        String last_active_at;
    }

    private static final class PetRow {
        String id;
        String owner_id;
        String owner_name;
        String name;
        String animal_type;
        String breed;
        BigDecimal age;
        BigDecimal weight;
        String photo_path;
        String created_at;
    }
}
