package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.common.error.ValidationException;
import az.miyav.data.http.HttpBinaryExchange;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.premium.PremiumRequest;
import az.miyav.domain.repository.PremiumRepository;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Receipts in the private {@code receipts} bucket; requests and reviews through RPCs. */
public final class SupabasePremiumRepository implements PremiumRepository {
    private static final int RECEIPT_LIMIT = 5 * 1024 * 1024;
    private static final Pattern RECEIPT_PATH = Pattern.compile("^[a-f0-9-]{36}/[a-f0-9-]{36}\\.jpg$");
    private static final String SELECT = "id,listing_id,owner_id,method,amount,days,receipt_path,status,note,reviewed_at,created_at";
    private static final Type ROWS = new TypeToken<List<RequestRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabasePremiumRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public String uploadReceipt(AuthSession session, byte[] jpeg) {
        requireSession(session);
        if (jpeg == null || jpeg.length < 3 || jpeg[0] != (byte) 0xFF || jpeg[1] != (byte) 0xD8) {
            throw new ValidationException("premium.receipt");
        }
        if (jpeg.length > RECEIPT_LIMIT) {
            throw new ValidationException("media.too_large");
        }
        String path = session.userId() + "/" + UUID.randomUUID() + ".jpg";
        HttpBinaryExchange response = client.sendBinary("POST", "/storage/v1/object/receipts/" + path, session, jpeg,
                "image/jpeg", Map.of());
        if (response.status() == 413) {
            throw new ValidationException("media.too_large");
        }
        if (response.status() == 401 || response.status() == 403) {
            throw new AccessDeniedException("access.owner");
        }
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        return path;
    }

    @Override
    public UUID request(AuthSession session, UUID listingId, PremiumRequest.Method method, String receiptPath) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("p_listing", listingId.toString());
        body.addProperty("p_method", method.databaseValue());
        body.addProperty("p_receipt", receiptPath);
        return UUID.fromString(JsonParser.parseString(call(session, "request_premium", body)).getAsString());
    }

    @Override
    public Optional<PremiumRequest> latest(AuthSession session, UUID listingId) {
        requireSession(session);
        HttpExchange response = client.send("GET", "/rest/v1/premium_requests?select=" + SELECT + "&listing_id=eq." + listingId
                + "&owner_id=eq." + session.userId() + "&order=created_at.desc&limit=1", session, null);
        ensureOk(response);
        return requests(response.body()).stream().findFirst();
    }

    @Override
    public List<PremiumRequest> queue(AuthSession session, PremiumRequest.Status status) {
        JsonObject body = new JsonObject();
        if (status == null) {
            body.add("p_status", JsonNull.INSTANCE);
        } else {
            body.addProperty("p_status", status.databaseValue());
        }
        try {
            return requests(call(session, "admin_premium_requests", body));
        } catch (AccessDeniedException exception) {
            throw new AccessDeniedException("access.admin");
        }
    }

    @Override
    public void review(AuthSession session, UUID requestId, boolean approve, String note) {
        JsonObject body = new JsonObject();
        body.addProperty("p_request", requestId.toString());
        body.addProperty("p_approve", approve);
        body.addProperty("p_note", note == null ? "" : note.trim());
        call(session, "review_premium", body);
    }

    @Override
    public byte[] receipt(AuthSession session, String receiptPath) {
        requireSession(session);
        if (receiptPath == null || !RECEIPT_PATH.matcher(receiptPath).matches()) {
            throw new ValidationException("premium.receipt");
        }
        HttpBinaryExchange response = client.sendBinary("GET", "/storage/v1/object/authenticated/receipts/" + receiptPath,
                session, null, null, Map.of());
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        return response.body();
    }

    private String call(AuthSession session, String function, JsonObject body) {
        requireSession(session);
        HttpExchange response = client.send("POST", "/rest/v1/rpc/" + function, session, body.toString());
        ensureOk(response);
        return response.body();
    }

    private List<PremiumRequest> requests(String json) {
        List<RequestRow> rows = client.gson().fromJson(json, ROWS);
        List<PremiumRequest> result = new ArrayList<>();
        for (RequestRow row : rows == null ? List.<RequestRow>of() : rows) {
            result.add(new PremiumRequest(UUID.fromString(row.id), UUID.fromString(row.listing_id), row.listing_name,
                    UUID.fromString(row.owner_id), row.owner_name, PremiumRequest.Method.fromDatabase(row.method),
                    row.amount, row.days, row.receipt_path, PremiumRequest.Status.fromDatabase(row.status), row.note,
                    row.created_at == null ? null : Timestamps.parse(row.created_at),
                    row.reviewed_at == null ? null : Timestamps.parse(row.reviewed_at),
                    row.premium_until == null ? null : Timestamps.parse(row.premium_until)));
        }
        return result;
    }

    private static void ensureOk(HttpExchange response) {
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }

    private static final class RequestRow {
        String id;
        String listing_id;
        String listing_name;
        String owner_id;
        String owner_name;
        String method;
        BigDecimal amount;
        int days;
        String receipt_path;
        String status;
        String note;
        String reviewed_at;
        String created_at;
        String premium_until;
    }
}
