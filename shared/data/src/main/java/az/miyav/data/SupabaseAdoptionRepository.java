package az.miyav.data;

import az.miyav.common.error.PersistenceException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.AdoptionRepository;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SupabaseAdoptionRepository implements AdoptionRepository {
    private static final String SELECT = "id,listing_id,applicant_id,message,status,created_at,updated_at";
    private static final Type ROWS = new TypeToken<List<ApplicationRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseAdoptionRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public AdoptionApplication save(AuthSession session, AdoptionApplication application) {
        return switch (application.status()) {
            case PENDING -> insert(session, application);
            case ACCEPTED -> rpc(session, "accept_adoption_application", application.id());
            case REJECTED -> rpc(session, "reject_adoption_application", application.id());
            case CANCELLED -> rpc(session, "cancel_adoption_application", application.id());
        };
    }

    @Override
    public Optional<AdoptionApplication> findAcceptedForPet(AuthSession session, UUID petId) {
        return rows(send(
                session,
                "/rest/v1/adoption_applications?select=" + SELECT + "&listing_id=eq." + petId + "&status=eq.accepted"
        )).stream().findFirst();
    }

    @Override
    public List<AdoptionApplication> findByPet(AuthSession session, UUID petId) {
        return rows(send(
                session,
                "/rest/v1/adoption_applications?select=" + SELECT + "&listing_id=eq." + petId + "&order=created_at.desc"
        ));
    }

    @Override
    public List<AdoptionApplication> findByApplicant(AuthSession session) {
        return rows(send(
                session,
                "/rest/v1/adoption_applications?select=" + SELECT + "&applicant_id=eq." + session.userId() + "&order=created_at.desc"
        ));
    }

    @Override
    public List<AdoptionApplication> findForAdmin(AuthSession session) {
        return rows(send(
                session,
                "/rest/v1/adoption_applications?select=" + SELECT + "&order=created_at.desc&limit=200"
        ));
    }

    private AdoptionApplication insert(AuthSession session, AdoptionApplication application) {
        JsonObject body = new JsonObject();
        body.addProperty("listing_id", application.petId().toString());
        body.addProperty("message", application.message());
        HttpExchange response = client.send(
                "POST",
                "/rest/v1/adoption_applications",
                session,
                body.toString(),
                Map.of("Prefer", "return=representation")
        );
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        return one(response);
    }

    private AdoptionApplication rpc(AuthSession session, String function, UUID applicationId) {
        JsonObject body = new JsonObject();
        body.addProperty("p_application", applicationId.toString());
        HttpExchange response = client.send("POST", "/rest/v1/rpc/" + function, session, body.toString());
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        return one(response);
    }

    private HttpExchange send(AuthSession session, String path) {
        HttpExchange response = client.send("GET", path, session, null);
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        return response;
    }

    private List<AdoptionApplication> rows(HttpExchange response) {
        String body = response.body().trim();
        List<ApplicationRow> parsed = new ArrayList<>();
        if (body.startsWith("{")) {
            ApplicationRow row = client.gson().fromJson(body, ApplicationRow.class);
            if (row != null) {
                parsed.add(row);
            }
        } else if (!body.isEmpty()) {
            List<ApplicationRow> list = client.gson().fromJson(body, ROWS);
            if (list != null) {
                parsed.addAll(list);
            }
        }
        List<AdoptionApplication> applications = new ArrayList<>();
        for (ApplicationRow row : parsed) {
            applications.add(new AdoptionApplication(
                    UUID.fromString(row.id),
                    UUID.fromString(row.listing_id),
                    UUID.fromString(row.applicant_id),
                    row.message,
                    ApplicationStatus.valueOf(row.status.toUpperCase()),
                    Timestamps.parse(row.created_at),
                    Timestamps.parse(row.updated_at)
            ));
        }
        return applications;
    }

    private AdoptionApplication one(HttpExchange response) {
        List<AdoptionApplication> applications = rows(response);
        if (applications.isEmpty()) {
            throw new PersistenceException("network.unavailable");
        }
        return applications.get(0);
    }

    private static final class ApplicationRow {
        String id;
        String listing_id;
        String applicant_id;
        String message;
        String status;
        String created_at;
        String updated_at;
    }
}
