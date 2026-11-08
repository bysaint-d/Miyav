package az.miyav.data.http;

import az.miyav.common.error.PersistenceException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

public final class OkHttpTransport implements HttpTransport {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;

    public OkHttpTransport() {
        this.client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public HttpExchange send(String method, URI uri, Map<String, String> headers, String body) {
        HttpBinaryExchange response = sendBinary(
                method,
                uri,
                headers,
                body == null ? null : body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "application/json; charset=utf-8"
        );
        return new HttpExchange(response.status(), new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public HttpBinaryExchange sendBinary(
            String method,
            URI uri,
            Map<String, String> headers,
            byte[] body,
            String contentType
    ) {
        Request.Builder request = new Request.Builder().url(uri.toString());
        headers.forEach(request::header);
        boolean hasBody = !"GET".equals(method) && !"DELETE".equals(method);
        if (!hasBody) {
            request.method(method, null);
        } else {
            MediaType mediaType = MediaType.parse(contentType == null ? "application/octet-stream" : contentType);
            request.method(method, RequestBody.create(body == null ? new byte[0] : body, mediaType));
        }
        try (Response response = client.newCall(request.build()).execute()) {
            byte[] responseBody = response.body() == null ? new byte[0] : response.body().bytes();
            return new HttpBinaryExchange(response.code(), responseBody);
        } catch (IOException exception) {
            throw new PersistenceException("network.unavailable", exception);
        }
    }
}
