package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.payout.PayoutDetails;
import az.miyav.domain.repository.PayoutRepository;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

/** {@code payout_details}: read through PostgREST (RLS: own row or admin), written by {@code save_payout_details}. */
public final class SupabasePayoutRepository implements PayoutRepository {
    private static final String SELECT = "holder_name,bank_name,iban,agreement_version,agreement_accepted_at";
    private static final Type ROWS = new TypeToken<List<PayoutRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabasePayoutRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public Optional<PayoutDetails> mine(AuthSession session) {
        requireSession(session);
        HttpExchange response = client.send("GET", "/rest/v1/payout_details?select=" + SELECT + "&user_id=eq." + session.userId(),
                session, null);
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<PayoutRow> rows = client.gson().fromJson(response.body(), ROWS);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        PayoutRow row = rows.get(0);
        return Optional.of(new PayoutDetails(row.holder_name, row.bank_name, row.iban, row.agreement_version,
                row.agreement_accepted_at == null ? null : Timestamps.parse(row.agreement_accepted_at)));
    }

    @Override
    public void save(AuthSession session, String holderName, String bankName, String iban, String agreementVersion) {
        requireSession(session);
        JsonObject body = new JsonObject();
        body.addProperty("p_holder", holderName);
        body.addProperty("p_bank", bankName);
        body.addProperty("p_iban", iban);
        body.addProperty("p_agreement", agreementVersion);
        HttpExchange response = client.send("POST", "/rest/v1/rpc/save_payout_details", session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }

    private static final class PayoutRow {
        String holder_name;
        String bank_name;
        String iban;
        String agreement_version;
        String agreement_accepted_at;
    }
}
