package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.moderation.Report;
import az.miyav.domain.repository.AdminRepository;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SupabaseAdminRepository implements AdminRepository {
    private static final Type ROWS = new TypeToken<List<ReportRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseAdminRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public void execute(AuthSession session, String action, UUID targetId, String value, String note) {
        JsonObject body = new JsonObject();
        body.addProperty("p_action", action);
        body.addProperty("p_id", targetId.toString());
        body.addProperty("p_value", value);
        body.addProperty("p_note", note == null ? "" : note);
        HttpExchange response = client.send("POST", "/rest/v1/rpc/admin_action", session, body.toString());
        if (response.status() / 100 != 2) {
            RuntimeException mapped = PostgrestErrors.from(response);
            if (mapped instanceof AccessDeniedException) {
                throw new AccessDeniedException("access.admin");
            }
            throw mapped;
        }
    }

    @Override
    public List<Report> listReports(AuthSession session) {
        HttpExchange response = client.send(
                "GET",
                "/rest/v1/reports?select=id,reporter_id,reported_id,reason,resolved,created_at&order=created_at.desc&limit=200",
                session,
                null
        );
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<ReportRow> rows = client.gson().fromJson(response.body(), ROWS);
        List<Report> reports = new ArrayList<>();
        if (rows == null) {
            return reports;
        }
        for (ReportRow row : rows) {
            reports.add(new Report(
                    UUID.fromString(row.id),
                    UUID.fromString(row.reporter_id),
                    UUID.fromString(row.reported_id),
                    row.reason,
                    row.resolved,
                    Timestamps.parse(row.created_at)
            ));
        }
        return reports;
    }

    private static final class ReportRow {
        String id;
        String reporter_id;
        String reported_id;
        String reason;
        boolean resolved;
        String created_at;
    }
}
