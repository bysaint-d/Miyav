package az.miyav.application;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.notification.Notification;
import az.miyav.domain.repository.NotificationRepository;

import java.util.List;
import java.util.UUID;

public final class NotificationService {
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public List<Notification> list(AuthSession session) {
        return notifications.list(session);
    }

    public int unreadCount(AuthSession session) {
        int count = 0;
        for (Notification notification : notifications.list(session)) {
            if (notification.unread() && notification.userId().equals(session.userId())) {
                count++;
            }
        }
        return count;
    }

    public void markRead(AuthSession session, UUID notificationId) {
        notifications.markRead(session, notificationId);
    }

    public void delete(AuthSession session, UUID notificationId) {
        notifications.delete(session, notificationId);
    }

    public void deleteAll(AuthSession session) {
        notifications.deleteAll(session);
    }
}
