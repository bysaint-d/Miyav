package az.miyav.data;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.LiveUpdates;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Supabase Realtime "postgres_changes" over the Phoenix websocket protocol. One socket per subscription,
 * a heartbeat every 25 seconds, and reconnects with back-off until the subscription is closed.
 */
public final class SupabaseRealtime implements LiveUpdates {
    private static final long HEARTBEAT_SECONDS = 25;
    private static final long MAX_BACKOFF_SECONDS = 30;
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "miyav-realtime");
        thread.setDaemon(true);
        return thread;
    });
    private final SupabaseConfig config;
    private final OkHttpClient http;

    public SupabaseRealtime(SupabaseConfig config) {
        this.config = config;
        this.http = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public Subscription watchConversation(Supplier<AuthSession> session, UUID conversationId, Runnable onChange) {
        return open(session, "messages", "conversation_id=eq." + conversationId, onChange);
    }

    @Override
    public Subscription watchNotifications(Supplier<AuthSession> session, Runnable onChange) {
        AuthSession current = session.get();
        if (current == null) {
            return NONE;
        }
        return open(session, "notifications", "user_id=eq." + current.userId(), onChange);
    }

    private Subscription open(Supplier<AuthSession> session, String table, String filter, Runnable onChange) {
        if (!config.configured()) {
            return NONE;
        }
        Channel channel = new Channel(session, table, filter, onChange);
        channel.connect();
        return channel;
    }

    static JsonObject joinMessage(String topic, String table, String filter, String accessToken, String ref) {
        JsonObject change = new JsonObject();
        change.addProperty("event", "INSERT");
        change.addProperty("schema", "public");
        change.addProperty("table", table);
        change.addProperty("filter", filter);
        JsonArray changes = new JsonArray();
        changes.add(change);
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("ack", false);
        broadcast.addProperty("self", false);
        JsonObject presence = new JsonObject();
        presence.addProperty("key", "");
        JsonObject channelConfig = new JsonObject();
        channelConfig.add("broadcast", broadcast);
        channelConfig.add("presence", presence);
        channelConfig.add("postgres_changes", changes);
        channelConfig.addProperty("private", false);
        JsonObject payload = new JsonObject();
        payload.add("config", channelConfig);
        payload.addProperty("access_token", accessToken);
        return envelope(topic, "phx_join", payload, ref, ref);
    }

    private static JsonObject envelope(String topic, String event, JsonObject payload, String ref, String joinRef) {
        JsonObject message = new JsonObject();
        message.addProperty("topic", topic);
        message.addProperty("event", event);
        message.add("payload", payload);
        message.addProperty("ref", ref);
        if (joinRef != null) {
            message.addProperty("join_ref", joinRef);
        }
        return message;
    }

    private final class Channel extends WebSocketListener implements Subscription {
        private final Supplier<AuthSession> session;
        private final String table;
        private final String filter;
        private final Runnable onChange;
        private final String topic = "realtime:miyav-" + UUID.randomUUID();
        private volatile boolean closed;
        private volatile boolean live;
        private WebSocket socket;
        private ScheduledFuture<?> heartbeat;
        private int ref;
        private int attempt;

        Channel(Supplier<AuthSession> session, String table, String filter, Runnable onChange) {
            this.session = session;
            this.table = table;
            this.filter = filter;
            this.onChange = onChange;
        }

        synchronized void connect() {
            if (closed) {
                return;
            }
            socket = http.newWebSocket(new Request.Builder().url(config.realtimeUrl()).build(), this);
        }

        @Override
        public boolean live() {
            return live;
        }

        @Override
        public synchronized void close() {
            closed = true;
            live = false;
            stopHeartbeat();
            if (socket != null) {
                socket.close(1000, "closed");
                socket = null;
            }
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            AuthSession current = session.get();
            if (current == null || closed) {
                webSocket.close(1000, "signed out");
                return;
            }
            String joinRef = nextRef();
            webSocket.send(joinMessage(topic, table, filter, current.accessToken(), joinRef).toString());
            synchronized (this) {
                stopHeartbeat();
                heartbeat = TIMER.scheduleAtFixedRate(() -> webSocket.send(
                                envelope("phoenix", "heartbeat", new JsonObject(), nextRef(), null).toString()),
                        HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            JsonObject message;
            try {
                message = JsonParser.parseString(text).getAsJsonObject();
            } catch (RuntimeException exception) {
                return;
            }
            String event = string(message, "event");
            if (!topic.equals(string(message, "topic"))) {
                return;
            }
            switch (event) {
                case "phx_reply" -> {
                    JsonElement payload = message.get("payload");
                    boolean ok = payload != null && payload.isJsonObject()
                            && "ok".equals(string(payload.getAsJsonObject(), "status"));
                    if (ok) {
                        live = true;
                        attempt = 0;
                    }
                }
                case "postgres_changes" -> onChange.run();
                case "phx_error", "phx_close" -> {
                    live = false;
                    webSocket.close(1000, event);
                }
                default -> {
                }
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            retry(webSocket);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable failure, Response response) {
            retry(webSocket);
        }

        private void retry(WebSocket failed) {
            synchronized (this) {
                if (failed != socket) {
                    return;
                }
                live = false;
                stopHeartbeat();
                if (closed) {
                    return;
                }
                long delay = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(attempt, 5));
                attempt++;
                TIMER.schedule(this::connect, delay, TimeUnit.SECONDS);
            }
        }

        private void stopHeartbeat() {
            if (heartbeat != null) {
                heartbeat.cancel(false);
                heartbeat = null;
            }
        }

        private synchronized String nextRef() {
            return String.valueOf(++ref);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
