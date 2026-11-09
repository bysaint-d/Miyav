package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.WalkListingRepository;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkListingStatus;
import az.miyav.domain.walk.WalkOffer;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code walk_listings} and {@code walk_offers} through PostgREST; assignment goes through RPCs. */
public final class SupabaseWalkListingRepository implements WalkListingRepository {
    static final String SELECT = "id,owner_id,pet_name,pet_species,starts_at,duration_minutes,price_per_hour,city,"
            + "latitude,longitude,location_name,notes,status,walker_id,agreed_price_per_hour,created_at,pet_id,pet_photo_path";
    private static final java.util.regex.Pattern PHOTO_PATH = java.util.regex.Pattern.compile(
            "[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.jpg");
    private static final String OFFER_SELECT = "id,listing_id,walker_id,message,status,price_per_hour,created_at";
    private static final Type ROWS = new TypeToken<List<ListingRow>>() {
    }.getType();
    private static final Type OFFER_ROWS = new TypeToken<List<OfferRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseWalkListingRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<WalkListing> findOpen(PetSpecies species, String city, int limit, int offset, AuthSession viewer) {
        StringBuilder path = new StringBuilder("/rest/v1/walk_listings?select=").append(SELECT)
                .append("&status=eq.open&starts_at=gt.").append(encode(Instant.now().minusSeconds(1800).toString()))
                .append("&order=starts_at.asc&limit=").append(limit).append("&offset=").append(offset);
        if (species != null) {
            path.append("&pet_species=eq.").append(species.databaseValue());
        }
        if (city != null) {
            path.append("&city=eq.").append(encode(city));
        }
        return listings(client.send("GET", path.toString(), viewer, null));
    }

    @Override
    public Optional<WalkListing> findById(UUID id, AuthSession viewer) {
        return listings(client.send("GET", "/rest/v1/walk_listings?select=" + SELECT + "&id=eq." + id, viewer, null))
                .stream().findFirst();
    }

    @Override
    public List<WalkListing> findMine(AuthSession session) {
        requireSession(session);
        UUID me = session.userId();
        StringBuilder filter = new StringBuilder("owner_id.eq.").append(me).append(",walker_id.eq.").append(me);
        List<WalkOffer> offered = myOffers(session);
        if (!offered.isEmpty()) {
            filter.append(",id.in.(");
            for (int i = 0; i < offered.size(); i++) {
                filter.append(i == 0 ? "" : ",").append(offered.get(i).listingId());
            }
            filter.append(')');
        }
        return listings(client.send("GET", "/rest/v1/walk_listings?select=" + SELECT
                + "&or=(" + filter + ")&order=starts_at.desc&limit=100", session, null));
    }

    @Override
    public WalkListing create(AuthSession session, WalkListingDetails details) {
        requireSession(session);
        HttpExchange response = client.send("POST", "/rest/v1/walk_listings?select=" + SELECT, session,
                body(details).toString(), Map.of("Prefer", "return=representation"));
        return single(response);
    }

    @Override
    public WalkListing update(AuthSession session, UUID listingId, WalkListingDetails details) {
        requireSession(session);
        HttpExchange response = client.send("PATCH", "/rest/v1/walk_listings?id=eq." + listingId + "&select=" + SELECT,
                session, body(details).toString(), Map.of("Prefer", "return=representation"));
        return single(response);
    }

    @Override
    public void cancel(AuthSession session, UUID listingId) {
        finish(session, listingId, WalkListingStatus.CANCELLED);
    }

    @Override
    public void complete(AuthSession session, UUID listingId) {
        finish(session, listingId, WalkListingStatus.COMPLETED);
    }

    @Override
    public List<WalkOffer> offersFor(AuthSession session, UUID listingId) {
        requireSession(session);
        return offers(client.send("GET", "/rest/v1/walk_offers?select=" + OFFER_SELECT + "&listing_id=eq." + listingId
                + "&order=created_at.asc", session, null));
    }

    @Override
    public List<WalkOffer> myOffers(AuthSession session) {
        requireSession(session);
        return offers(client.send("GET", "/rest/v1/walk_offers?select=" + OFFER_SELECT + "&walker_id=eq." + session.userId()
                + "&order=created_at.desc&limit=100", session, null));
    }

    @Override
    public UUID offer(AuthSession session, UUID listingId, BigDecimal pricePerHour, String message) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("p_listing", listingId.toString());
        body.addProperty("p_price", pricePerHour);
        body.addProperty("p_message", message);
        HttpExchange response = client.send("POST", "/rest/v1/rpc/make_walk_offer", session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        return UUID.fromString(JsonParser.parseString(response.body()).getAsString());
    }

    @Override
    public void withdraw(AuthSession session, UUID offerId) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("status", WalkOffer.Status.WITHDRAWN.databaseValue());
        HttpExchange response = client.send("PATCH", "/rest/v1/walk_offers?id=eq." + offerId, session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    @Override
    public void respond(AuthSession session, UUID offerId, boolean accept) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("p_offer", offerId.toString());
        body.addProperty("p_accept", accept);
        rpc(session, "respond_walk_offer", body);
    }

    private void finish(AuthSession session, UUID listingId, WalkListingStatus status) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("p_listing", listingId.toString());
        body.addProperty("p_status", status.databaseValue());
        rpc(session, "finish_walk_listing", body);
    }

    private void rpc(AuthSession session, String function, JsonObject body) {
        HttpExchange response = client.send("POST", "/rest/v1/rpc/" + function, session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    static JsonObject body(WalkListingDetails details) {
        JsonObject body = new JsonObject();
        body.addProperty("pet_name", details.petName());
        body.addProperty("pet_species", details.species().databaseValue());
        body.addProperty("starts_at", details.startsAt().toString());
        body.addProperty("duration_minutes", details.durationMinutes());
        body.addProperty("price_per_hour", details.pricePerHour());
        body.addProperty("city", details.city());
        ListingLocation area = details.area();
        if (area == null) {
            body.add("latitude", JsonNull.INSTANCE);
            body.add("longitude", JsonNull.INSTANCE);
            body.addProperty("location_name", "");
        } else {
            body.addProperty("latitude", area.latitude());
            body.addProperty("longitude", area.longitude());
            body.addProperty("location_name", area.name());
        }
        body.addProperty("notes", details.notes());
        if (details.petId().isPresent()) {
            body.addProperty("pet_id", details.petId().get().toString());
        } else {
            body.add("pet_id", JsonNull.INSTANCE);
        }
        return body;
    }

    @Override
    public byte[] petPhoto(AuthSession viewer, String path) {
        if (path == null || !PHOTO_PATH.matcher(path).matches()) {
            throw new az.miyav.common.error.ValidationException("validation.pet");
        }
        az.miyav.data.http.HttpBinaryExchange response = client.sendBinary("GET",
                "/storage/v1/object/authenticated/pets/" + path, viewer, null, null, Map.of());
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        return response.body();
    }

    private WalkListing single(HttpExchange response) {
        List<WalkListing> saved = listings(response);
        if (saved.isEmpty()) {
            throw new PersistenceException("network.unavailable");
        }
        return saved.get(0);
    }

    private List<WalkListing> listings(HttpExchange response) {
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<ListingRow> rows = client.gson().fromJson(response.body(), ROWS);
        List<WalkListing> result = new ArrayList<>();
        for (ListingRow row : rows == null ? List.<ListingRow>of() : rows) {
            ListingLocation area = row.latitude == null || row.longitude == null
                    || !ListingLocation.inArea(row.latitude, row.longitude)
                    ? null : new ListingLocation(row.latitude, row.longitude, row.location_name);
            WalkListingDetails details = new WalkListingDetails(row.pet_name, species(row.pet_species),
                    Timestamps.parse(row.starts_at), row.duration_minutes, row.price_per_hour, row.city, area, row.notes,
                    row.pet_id == null ? null : UUID.fromString(row.pet_id));
            result.add(new WalkListing(UUID.fromString(row.id), UUID.fromString(row.owner_id), details,
                    WalkListingStatus.fromDatabase(row.status), row.walker_id == null ? null : UUID.fromString(row.walker_id),
                    row.agreed_price_per_hour, row.created_at == null ? null : Timestamps.parse(row.created_at))
                    .withPetPhoto(row.pet_photo_path));
        }
        return result;
    }

    private List<WalkOffer> offers(HttpExchange response) {
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<OfferRow> rows = client.gson().fromJson(response.body(), OFFER_ROWS);
        List<WalkOffer> result = new ArrayList<>();
        for (OfferRow row : rows == null ? List.<OfferRow>of() : rows) {
            result.add(new WalkOffer(UUID.fromString(row.id), UUID.fromString(row.listing_id), UUID.fromString(row.walker_id),
                    row.message, WalkOffer.Status.fromDatabase(row.status), row.price_per_hour,
                    row.created_at == null ? null : Timestamps.parse(row.created_at)));
        }
        return result;
    }

    private static PetSpecies species(String value) {
        try {
            return PetSpecies.fromDatabase(value);
        } catch (IllegalArgumentException exception) {
            return PetSpecies.OTHER;
        }
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class ListingRow {
        String id;
        String owner_id;
        String pet_name;
        String pet_species;
        String starts_at;
        int duration_minutes;
        BigDecimal price_per_hour;
        String city;
        Double latitude;
        Double longitude;
        String location_name;
        String notes;
        String status;
        String walker_id;
        BigDecimal agreed_price_per_hour;
        String created_at;
        String pet_id;
        String pet_photo_path;
    }

    private static final class OfferRow {
        String id;
        String listing_id;
        String walker_id;
        String message;
        String status;
        BigDecimal price_per_hour;
        String created_at;
    }
}
