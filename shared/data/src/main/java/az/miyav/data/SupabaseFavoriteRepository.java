package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.FavoriteRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Account favourites in {@code adoption_favorites}; RLS limits every row to its owner. */
public final class SupabaseFavoriteRepository implements FavoriteRepository {
    private static final Type ROWS = new TypeToken<List<Row>>() {
    }.getType();
    private static final Type MERGE_ROWS = new TypeToken<List<MergeRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseFavoriteRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public Set<UUID> list(AuthSession session) {
        requireSession(session);
        HttpExchange response = client.send("GET", "/rest/v1/adoption_favorites?select=listing_id", session, null);
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<Row> rows = client.gson().fromJson(response.body(), ROWS);
        Set<UUID> ids = new LinkedHashSet<>();
        if (rows != null) {
            for (Row row : rows) {
                ids.add(UUID.fromString(row.listing_id));
            }
        }
        return ids;
    }

    @Override
    public void add(AuthSession session, UUID listingId) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("listing_id", listingId.toString());
        HttpExchange response = client.send("POST", "/rest/v1/adoption_favorites", session, body.toString(),
                Map.of("Prefer", "resolution=ignore-duplicates,return=minimal"));
        if (response.status() / 100 != 2 && response.status() != 409) {
            throw PostgrestErrors.from(response);
        }
    }

    @Override
    public void remove(AuthSession session, UUID listingId) {
        requireSession(session);
        HttpExchange response = client.send("DELETE", "/rest/v1/adoption_favorites?listing_id=eq." + listingId, session, null);
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    @Override
    public MergeResult merge(AuthSession session, Collection<UUID> listingIds) {
        requireSession(session);
        if (listingIds.isEmpty()) {
            return MergeResult.EMPTY;
        }
        JsonArray ids = new JsonArray();
        for (UUID id : listingIds) {
            ids.add(id.toString());
        }
        JsonObject body = new JsonObject();
        body.add("p_listing_ids", ids);
        HttpExchange response = client.send("POST", "/rest/v1/rpc/merge_adoption_favorites", session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<MergeRow> rows = client.gson().fromJson(response.body(), MERGE_ROWS);
        if (rows == null || rows.isEmpty()) {
            return MergeResult.EMPTY;
        }
        MergeRow row = rows.get(0);
        return new MergeResult(row.added, row.already_saved, row.unavailable);
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }

    private static final class Row {
        String listing_id;
    }

    private static final class MergeRow {
        int added;
        int already_saved;
        int unavailable;
    }
}
