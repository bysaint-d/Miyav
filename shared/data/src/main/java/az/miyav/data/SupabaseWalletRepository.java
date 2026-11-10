package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.WalletRepository;
import az.miyav.domain.wallet.Wallet;
import az.miyav.domain.wallet.Withdrawal;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** {@code withdrawals} read through PostgREST (own rows or admin); balance, requests and reviews through RPCs. */
public final class SupabaseWalletRepository implements WalletRepository {
    private static final String SELECT = "id,receipt_no,walker_id,amount,fee_percent,fee,net,holder_name,bank_name,iban,status,"
            + "transfer_reference,note,processed_at,created_at";
    private static final Type WALLET_ROWS = new TypeToken<List<WalletRow>>() {
    }.getType();
    private static final Type ROWS = new TypeToken<List<WithdrawalRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseWalletRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public Wallet wallet(AuthSession session) {
        List<WalletRow> rows = client.gson().fromJson(call(session, "my_wallet", new JsonObject()), WALLET_ROWS);
        if (rows == null || rows.isEmpty()) {
            throw new PersistenceException("network.unavailable");
        }
        WalletRow row = rows.get(0);
        return new Wallet(row.earned, row.held == null ? BigDecimal.ZERO : row.held, row.pending, row.withdrawn, row.available,
                row.completed, row.fee_percent, row.minimum);
    }

    @Override
    public List<Withdrawal> mine(AuthSession session) {
        requireSession(session);
        HttpExchange response = client.send("GET", "/rest/v1/withdrawals?select=" + SELECT + "&walker_id=eq." + session.userId()
                + "&order=created_at.desc&limit=100", session, null);
        ensureOk(response);
        return withdrawals(response.body());
    }

    @Override
    public UUID request(AuthSession session, BigDecimal amount) {
        JsonObject body = new JsonObject();
        body.addProperty("p_amount", amount);
        return UUID.fromString(JsonParser.parseString(call(session, "request_withdrawal", body)).getAsString());
    }

    @Override
    public void cancel(AuthSession session, UUID withdrawalId) {
        JsonObject body = new JsonObject();
        body.addProperty("p_request", withdrawalId.toString());
        call(session, "cancel_withdrawal", body);
    }

    @Override
    public List<Withdrawal> queue(AuthSession session, Withdrawal.Status status) {
        JsonObject body = new JsonObject();
        if (status == null) {
            body.add("p_status", JsonNull.INSTANCE);
        } else {
            body.addProperty("p_status", status.databaseValue());
        }
        try {
            return withdrawals(call(session, "admin_withdrawals", body));
        } catch (AccessDeniedException exception) {
            throw new AccessDeniedException("access.admin");
        }
    }

    @Override
    public void review(AuthSession session, UUID withdrawalId, boolean paid, String reference, String note) {
        JsonObject body = new JsonObject();
        body.addProperty("p_request", withdrawalId.toString());
        body.addProperty("p_paid", paid);
        body.addProperty("p_reference", reference == null ? "" : reference);
        body.addProperty("p_note", note == null ? "" : note);
        call(session, "review_withdrawal", body);
    }

    private String call(AuthSession session, String function, JsonObject body) {
        requireSession(session);
        HttpExchange response = client.send("POST", "/rest/v1/rpc/" + function, session, body.toString());
        ensureOk(response);
        return response.body();
    }

    private List<Withdrawal> withdrawals(String json) {
        List<WithdrawalRow> rows = client.gson().fromJson(json, ROWS);
        List<Withdrawal> result = new ArrayList<>();
        for (WithdrawalRow row : rows == null ? List.<WithdrawalRow>of() : rows) {
            Withdrawal withdrawal = new Withdrawal(UUID.fromString(row.id), row.receipt_no,
                    row.walker_id == null ? null : UUID.fromString(row.walker_id), row.amount, row.fee_percent, row.fee, row.net,
                    row.holder_name, row.bank_name, row.iban, Withdrawal.Status.fromDatabase(row.status), row.transfer_reference,
                    row.note, row.processed_at == null ? null : Timestamps.parse(row.processed_at),
                    row.created_at == null ? null : Timestamps.parse(row.created_at));
            if (row.walker_name != null) {
                withdrawal = withdrawal.withWalker(row.walker_name, row.available, row.completed == null ? 0 : row.completed);
            }
            result.add(withdrawal);
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

    private static final class WalletRow {
        BigDecimal earned;
        BigDecimal held;
        BigDecimal pending;
        BigDecimal withdrawn;
        BigDecimal available;
        int completed;
        BigDecimal fee_percent;
        BigDecimal minimum;
    }

    private static final class WithdrawalRow {
        String id;
        long receipt_no;
        String walker_id;
        String walker_name;
        BigDecimal amount;
        BigDecimal fee_percent;
        BigDecimal fee;
        BigDecimal net;
        String holder_name;
        String bank_name;
        String iban;
        String status;
        String transfer_reference;
        String note;
        String processed_at;
        String created_at;
        BigDecimal available;
        Integer completed;
    }
}
