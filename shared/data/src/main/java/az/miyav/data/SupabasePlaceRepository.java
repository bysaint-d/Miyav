package az.miyav.data;

import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.place.Place;
import az.miyav.domain.repository.PlaceRepository;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code public.places}: read with the anonymous role, written by admins (RLS checks private.is_admin()). */
public final class SupabasePlaceRepository implements PlaceRepository {
    private static final String SELECT = "id,kind,name,address,phone,opening_hours,latitude,longitude,is_24h,whatsapp,verified,active,"
            + "sponsored,website,description";
    private static final Type ROWS = new TypeToken<List<Row>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabasePlaceRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<Place> list(Place.Kind kind) {
        return rows(client.send("GET", "/rest/v1/places?select=" + SELECT
                + "&active=eq.true&kind=eq." + kind.databaseValue() + "&limit=1000", null, null));
    }

    @Override
    public List<Place> listAll(AuthSession admin) {
        return rows(client.send("GET", "/rest/v1/places?select=" + SELECT + "&order=kind.asc,name.asc&limit=2000", admin, null));
    }

    @Override
    public Place save(AuthSession admin, Place place) {
        JsonObject body = new JsonObject();
        body.addProperty("id", place.id().toString());
        body.addProperty("kind", place.kind().databaseValue());
        body.addProperty("name", place.name());
        nullable(body, "address", place.address().orElse(null));
        nullable(body, "phone", place.phone().orElse(null));
        nullable(body, "opening_hours", place.openingHours().orElse(null));
        nullable(body, "whatsapp", place.whatsapp().orElse(null));
        body.addProperty("latitude", place.latitude());
        body.addProperty("longitude", place.longitude());
        body.addProperty("is_24h", place.open24h());
        body.addProperty("verified", place.verified());
        body.addProperty("active", place.active());
        body.addProperty("sponsored", place.sponsored());
        nullable(body, "website", place.website().orElse(null));
        nullable(body, "description", place.description().orElse(null));
        List<Place> saved = rows(client.send("POST", "/rest/v1/places?on_conflict=id&select=" + SELECT, admin, body.toString(),
                Map.of("Prefer", "resolution=merge-duplicates,return=representation")));
        if (saved.isEmpty()) {
            throw new az.miyav.common.error.AccessDeniedException("access.admin");
        }
        return saved.get(0);
    }

    private static void nullable(JsonObject body, String key, String value) {
        if (value == null) {
            body.add(key, JsonNull.INSTANCE);
        } else {
            body.addProperty(key, value);
        }
    }

    private List<Place> rows(HttpExchange response) {
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<Row> rows = client.gson().fromJson(response.body(), ROWS);
        List<Place> places = new ArrayList<>();
        for (Row row : rows == null ? List.<Row>of() : rows) {
            places.add(new Place(UUID.fromString(row.id), Place.Kind.fromDatabase(row.kind), row.name, row.address,
                    row.phone, row.opening_hours, row.latitude, row.longitude, row.is_24h, row.whatsapp, row.verified,
                    row.active == null || row.active, row.sponsored, row.website, row.description));
        }
        return places;
    }

    private static final class Row {
        String id;
        String kind;
        String name;
        String address;
        String phone;
        String opening_hours;
        double latitude;
        double longitude;
        boolean is_24h;
        String whatsapp;
        boolean verified;
        Boolean active;
        boolean sponsored;
        String website;
        String description;
    }
}
