package az.miyav.android.push;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import az.miyav.android.MiyavApplication;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Receives MIYAV pushes (data messages from the dispatch-push function) and new Firebase tokens. */
public class MiyavMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        Push.tokenChanged((MiyavApplication) getApplication(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String title = data.getOrDefault("title", "");
        String body = data.getOrDefault("body", "");
        if (title.isEmpty() && body.isEmpty()) {
            return;
        }
        PhoneNotifications.show(this, data.get("id"), title, body, data.getOrDefault("conversation_id", ""),
                millis(data.get("created_at")));
    }

    private static long millis(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            return Instant.parse(timestamp.replace(' ', 'T').replaceFirst("\\+00(:00)?$", "Z")).toEpochMilli();
        } catch (DateTimeParseException exception) {
            return System.currentTimeMillis();
        }
    }
}
