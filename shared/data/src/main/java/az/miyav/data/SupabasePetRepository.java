package az.miyav.data;

import az.miyav.common.error.PersistenceException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.ListingExtras;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetFactory;
import az.miyav.domain.pet.PetFilters;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.pet.PetSort;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.pet.Vaccination;
import az.miyav.domain.repository.PetRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SupabasePetRepository implements PetRepository {
    static final String SELECT = "id,owner_id,name,species,breed,age_months,gender,city,description,status,photos,"
            + "vaccination,latitude,longitude,location_name,premium_until";
    private static final Type ROWS = new TypeToken<List<ListingRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabasePetRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<Pet> findPublished(PetQuery query, AuthSession session) {
        PetFilters filters = query.filters();
        StringBuilder path = new StringBuilder("/rest/v1/adoption_listings?select=")
                .append(SELECT)
                .append("&status=eq.active&order=").append(order(filters.sort()))
                .append("&limit=").append(query.limit())
                .append("&offset=").append(query.offset());
        if (!query.city().isEmpty()) {
            path.append("&city=eq.").append(encode(query.city()));
        }
        if (query.species() != null) {
            path.append("&species=eq.").append(query.species().databaseValue());
        }
        if (filters.gender() != null) {
            path.append("&gender=eq.").append(filters.gender().databaseValue());
        }
        if (filters.age().minMonths() != null) {
            path.append("&age_months=gte.").append(filters.age().minMonths());
        }
        if (filters.age().maxMonthsExclusive() != null) {
            path.append("&age_months=lt.").append(filters.age().maxMonthsExclusive());
        }
        if (filters.vaccinatedOnly()) {
            path.append("&vaccination=eq.").append(Vaccination.YES.databaseValue());
        }
        String search = sanitize(query.search());
        if (!search.isEmpty()) {
            String like = encode("*" + search + "*");
            path.append("&or=(")
                    .append("name.ilike.").append(like)
                    .append(",breed.ilike.").append(like)
                    .append(")");
        }
        return mapAll(client.send("GET", path.toString(), session, null));
    }

    @Override
    public List<Pet> findNear(double latitude, double longitude, int radiusKm, PetQuery query, AuthSession session) {
        JsonObject body = new JsonObject();
        body.addProperty("p_lat", latitude);
        body.addProperty("p_lng", longitude);
        body.addProperty("p_radius_km", radiusKm);
        if (query.species() == null) {
            body.add("p_species", JsonNull.INSTANCE);
        } else {
            body.addProperty("p_species", query.species().databaseValue());
        }
        body.addProperty("p_search", sanitize(query.search()));
        body.addProperty("p_limit", query.limit());
        body.addProperty("p_offset", query.offset());
        PetFilters filters = query.filters();
        if (filters.gender() != null) {
            body.addProperty("p_gender", filters.gender().databaseValue());
        }
        if (filters.age().minMonths() != null) {
            body.addProperty("p_min_age", filters.age().minMonths());
        }
        if (filters.age().maxMonthsExclusive() != null) {
            body.addProperty("p_max_age", filters.age().maxMonthsExclusive());
        }
        if (filters.vaccinatedOnly()) {
            body.addProperty("p_vaccinated", true);
        }
        return mapAll(client.send("POST", "/rest/v1/rpc/listings_near", session, body.toString()));
    }

    /** Current premium listings come first in every sort order ({@code is_premium} is a computed field). */
    private static String order(PetSort sort) {
        return "is_premium.desc," + switch (sort) {
            case OLDEST -> "created_at.asc";
            case YOUNGEST -> "age_months.asc,created_at.desc";
            case ELDEST -> "age_months.desc,created_at.desc";
            default -> "created_at.desc";
        };
    }

    @Override
    public List<Pet> findPublishedByOwner(UUID ownerId, AuthSession session) {
        return mapAll(client.send(
                "GET",
                "/rest/v1/adoption_listings?select=" + SELECT + "&owner_id=eq." + ownerId
                        + "&status=eq.active&order=created_at.desc&limit=20",
                session,
                null
        ));
    }

    List<Pet> findPublishedByIds(java.util.Collection<UUID> ids, AuthSession session) {
        if (ids.isEmpty()) {
            return List.of();
        }
        StringBuilder list = new StringBuilder();
        for (UUID id : ids) {
            if (list.length() > 0) {
                list.append(',');
            }
            list.append(id);
        }
        return mapAll(client.send(
                "GET",
                "/rest/v1/adoption_listings?select=" + SELECT + "&status=eq.active&id=in.(" + list + ")&order=created_at.desc",
                session,
                null
        ));
    }

    @Override
    public Optional<Pet> findById(UUID id, AuthSession session) {
        List<Pet> pets = mapAll(client.send(
                "GET",
                "/rest/v1/adoption_listings?select=" + SELECT + "&id=eq." + id,
                session,
                null
        ));
        return pets.stream().findFirst();
    }

    @Override
    public List<Pet> findOwned(AuthSession session) {
        return mapAll(client.send(
                "GET",
                "/rest/v1/adoption_listings?select=" + SELECT + "&owner_id=eq." + session.userId() + "&order=updated_at.desc",
                session,
                null
        ));
    }

    @Override
    public List<Pet> findForAdmin(AuthSession session) {
        return mapAll(client.send(
                "GET",
                "/rest/v1/adoption_listings?select=" + SELECT + "&order=updated_at.desc&limit=200",
                session,
                null
        ));
    }

    @Override
    public Pet save(AuthSession session, Pet pet) {
        boolean exists = findById(pet.id(), session).isPresent();
        JsonObject body = listingBody(pet, exists);
        HttpExchange response = exists
                ? client.send(
                "PATCH",
                "/rest/v1/adoption_listings?id=eq." + pet.id() + "&select=" + SELECT,
                session,
                body.toString(),
                Map.of("Prefer", "return=representation")
        )
                : client.send(
                "POST",
                "/rest/v1/adoption_listings?select=" + SELECT,
                session,
                body.toString(),
                Map.of("Prefer", "return=representation")
        );
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<Pet> saved = mapAll(response);
        if (saved.isEmpty()) {
            throw new PersistenceException("network.unavailable");
        }
        return saved.get(0);
    }

    static JsonObject listingBody(Pet pet, boolean update) {
        JsonObject body = new JsonObject();
        if (!update) {
            body.addProperty("id", pet.id().toString());
        }
        body.addProperty("name", pet.name());
        body.addProperty("species", pet.species().databaseValue());
        body.addProperty("breed", pet.breed());
        body.addProperty("age_months", pet.ageMonths());
        body.addProperty("gender", pet.gender().databaseValue());
        body.addProperty("city", pet.city());
        body.addProperty("description", pet.description());
        body.addProperty("status", ListingStatusMapper.toDatabase(pet.status()));
        body.addProperty("vaccination", pet.vaccination().databaseValue());
        ListingLocation location = pet.location();
        if (location == null) {
            body.add("latitude", JsonNull.INSTANCE);
            body.add("longitude", JsonNull.INSTANCE);
            body.addProperty("location_name", "");
        } else {
            body.addProperty("latitude", location.latitude());
            body.addProperty("longitude", location.longitude());
            body.addProperty("location_name", location.name());
        }
        JsonArray photos = new JsonArray();
        for (String path : pet.photoPaths()) {
            photos.add(path);
        }
        body.add("photos", photos);
        return body;
    }

    private List<Pet> mapAll(HttpExchange response) {
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        String payload = response.body().trim();
        List<ListingRow> rows = new ArrayList<>();
        if (payload.startsWith("{")) {
            ListingRow row = client.gson().fromJson(payload, ListingRow.class);
            if (row != null) {
                rows.add(row);
            }
        } else if (!payload.isEmpty()) {
            List<ListingRow> parsed = client.gson().fromJson(payload, ROWS);
            if (parsed != null) {
                rows.addAll(parsed);
            }
        }
        List<Pet> pets = new ArrayList<>();
        for (ListingRow row : rows) {
            pets.add(toPet(row));
        }
        return pets;
    }

    private static Pet toPet(ListingRow row) {
        List<String> photos = row.photos == null ? List.of() : row.photos;
        ListingLocation location = null;
        if (row.latitude != null && row.longitude != null
                && ListingLocation.inArea(row.latitude, row.longitude)) {
            location = new ListingLocation(row.latitude, row.longitude, row.location_name);
        }
        return PetFactory.create(
                PetSpecies.fromDatabase(row.species),
                UUID.fromString(row.id),
                UUID.fromString(row.owner_id),
                row.name,
                row.breed,
                row.age_months,
                PetGender.fromDatabase(row.gender),
                row.city,
                row.description,
                ListingStatusMapper.toDomain(row.status),
                photos,
                new ListingExtras(Vaccination.fromDatabase(row.vaccination), location,
                        row.premium_until == null ? null : Timestamps.parse(row.premium_until))
        );
    }

    static String sanitize(String search) {
        if (search == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < search.length(); i++) {
            char character = search.charAt(i);
            if (Character.isLetterOrDigit(character) || character == ' ' || character == '-') {
                kept.append(character);
            }
        }
        return kept.toString().trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class ListingRow {
        String id;
        String owner_id;
        String name;
        String species;
        String breed;
        int age_months;
        String gender;
        String city;
        String description;
        String status;
        List<String> photos;
        String vaccination;
        Double latitude;
        Double longitude;
        String location_name;
        String premium_until;
    }
}
