package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.common.error.ValidationException;
import az.miyav.data.http.HttpBinaryExchange;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.pet.PetProfileDraft;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.OwnPetRepository;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Own pets through PostgREST and the private {@code pets} storage bucket. */
public final class SupabaseOwnPetRepository implements OwnPetRepository {
    private static final String SELECT = "id,owner_id,name,animal_type,breed,age,weight,photo_path,care_notes,created_at";
    private static final int PHOTO_LIMIT = 5 * 1024 * 1024;
    private static final Pattern PHOTO_PATH = Pattern.compile("^[a-f0-9-]{36}/[a-f0-9-]{36}/[a-f0-9-]{36}\\.jpg$");
    private static final Type ROWS = new TypeToken<List<Row>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseOwnPetRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<PetProfile> mine(AuthSession session) {
        requireSession(session);
        return rows(client.send("GET", "/rest/v1/pets?select=" + SELECT + "&owner_id=eq." + session.userId()
                + "&order=created_at.asc", session, null));
    }

    @Override
    public PetProfile create(AuthSession session, PetProfileDraft draft) {
        requireSession(session);
        JsonObject body = body(draft);
        body.addProperty("owner_id", session.userId().toString());
        return single(client.send("POST", "/rest/v1/pets?select=" + SELECT, session, body.toString(),
                Map.of("Prefer", "return=representation")));
    }

    @Override
    public PetProfile update(AuthSession session, UUID petId, PetProfileDraft draft) {
        requireSession(session);
        return single(client.send("PATCH", "/rest/v1/pets?id=eq." + petId + "&select=" + SELECT, session,
                body(draft).toString(), Map.of("Prefer", "return=representation")));
    }

    @Override
    public void delete(AuthSession session, UUID petId) {
        requireSession(session);
        HttpExchange response = client.send("DELETE", "/rest/v1/pets?id=eq." + petId, session, null);
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    @Override
    public PetProfile setPhoto(AuthSession session, UUID petId, byte[] jpeg) {
        requireSession(session);
        if (jpeg == null || jpeg.length < 3 || jpeg[0] != (byte) 0xFF || jpeg[1] != (byte) 0xD8) {
            throw new ValidationException("validation.pet");
        }
        if (jpeg.length > PHOTO_LIMIT) {
            throw new ValidationException("media.too_large");
        }
        String path = session.userId() + "/" + petId + "/" + UUID.randomUUID() + ".jpg";
        HttpBinaryExchange upload = client.sendBinary("POST", "/storage/v1/object/pets/" + path, session, jpeg,
                "image/jpeg", Map.of());
        if (upload.status() == 413) {
            throw new ValidationException("media.too_large");
        }
        if (upload.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        JsonObject body = new JsonObject();
        body.addProperty("photo_path", path);
        return single(client.send("PATCH", "/rest/v1/pets?id=eq." + petId + "&select=" + SELECT, session,
                body.toString(), Map.of("Prefer", "return=representation")));
    }

    @Override
    public byte[] photo(AuthSession session, String path) {
        requireSession(session);
        if (path == null || !PHOTO_PATH.matcher(path).matches()) {
            throw new ValidationException("validation.pet");
        }
        HttpBinaryExchange response = client.sendBinary("GET", "/storage/v1/object/authenticated/pets/" + path,
                session, null, null, Map.of());
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        return response.body();
    }

    private static JsonObject body(PetProfileDraft draft) {
        JsonObject body = new JsonObject();
        body.addProperty("name", draft.name());
        body.addProperty("animal_type", draft.species().databaseValue());
        body.addProperty("breed", draft.breed());
        body.addProperty("age", draft.ageYears());
        body.addProperty("weight", draft.weightKg());
        body.addProperty("care_notes", draft.careNotes());
        return body;
    }

    private PetProfile single(HttpExchange response) {
        List<PetProfile> saved = rows(response);
        if (saved.isEmpty()) {
            throw new AccessDeniedException("access.owner");
        }
        return saved.get(0);
    }

    private List<PetProfile> rows(HttpExchange response) {
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<Row> rows = client.gson().fromJson(response.body(), ROWS);
        List<PetProfile> result = new ArrayList<>();
        for (Row row : rows == null ? List.<Row>of() : rows) {
            PetSpecies species;
            try {
                species = PetSpecies.fromDatabase(row.animal_type);
            } catch (IllegalArgumentException exception) {
                species = PetSpecies.OTHER;
            }
            result.add(new PetProfile(UUID.fromString(row.id), UUID.fromString(row.owner_id), "", row.name, species,
                    row.breed, row.age, row.weight, row.photo_path, row.care_notes,
                    row.created_at == null ? null : Timestamps.parse(row.created_at)));
        }
        return result;
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }

    private static final class Row {
        String id;
        String owner_id;
        String name;
        String animal_type;
        String breed;
        BigDecimal age;
        BigDecimal weight;
        String photo_path;
        String care_notes;
        String created_at;
    }
}
