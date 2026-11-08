package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.notification.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
    List<Notification> list(AuthSession session);

    void markRead(AuthSession session, UUID notificationId);
}
