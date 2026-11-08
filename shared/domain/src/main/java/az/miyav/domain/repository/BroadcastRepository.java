package az.miyav.domain.repository;

import az.miyav.domain.admin.Broadcast;
import az.miyav.domain.admin.BroadcastAudience;
import az.miyav.domain.auth.AuthSession;

import java.util.List;

/** Admin push broadcasts. The server allows these only for administrators and at most 10 per hour. */
public interface BroadcastRepository {
    /** How many members a broadcast would reach; {@code city} null means every city. */
    int audienceSize(AuthSession session, BroadcastAudience audience, String city);

    /** Sends the notification and returns the number of recipients. */
    int send(AuthSession session, String title, String body, BroadcastAudience audience, String city);

    List<Broadcast> recent(AuthSession session);
}
