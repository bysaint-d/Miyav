package az.miyav.data;

import az.miyav.common.error.PersistenceException;
import az.miyav.data.http.HttpExchange;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.notification.Notification;
import az.miyav.domain.notification.NotificationType;
import az.miyav.domain.repository.NotificationRepository;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SupabaseNotificationRepository implements NotificationRepository {
    private static final Type ROWS = new TypeToken<List<NotificationRow>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabaseNotificationRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<Notification> list(AuthSession session) {
        HttpExchange response = client.send(
                "GET",
                "/rest/v1/notifications?select=id,user_id,title,body,created_at,read_at&order=created_at.desc",
                session,
                null
        );
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
        List<NotificationRow> rows = client.gson().fromJson(response.body(), ROWS);
        List<Notification> notifications = new ArrayList<>();
        if (rows == null) {
            return notifications;
        }
        for (NotificationRow row : rows) {
            notifications.add(new Notification(
                    UUID.fromString(row.id),
                    UUID.fromString(row.user_id),
                    NotificationType.SYSTEM,
                    row.title,
                    row.body,
                    Timestamps.parse(row.created_at),
                    row.read_at == null ? null : Timestamps.parse(row.read_at)
            ));
        }
        return notifications;
    }

    @Override
    public void markRead(AuthSession session, UUID notificationId) {
        JsonObject body = new JsonObject();
        body.addProperty("read_at", Instant.now().toString());
        HttpExchange response = client.send(
                "PATCH",
                "/rest/v1/notifications?id=eq." + notificationId,
                session,
                body.toString(),
                Map.of("Prefer", "return=minimal")
        );
        if (response.status() / 100 != 2) {
            throw new PersistenceException("network.unavailable");
        }
    }

    private static final class NotificationRow {
        String id;
        String user_id;
        String title;
        String body;
        String created_at;
        String read_at;
    }
}
