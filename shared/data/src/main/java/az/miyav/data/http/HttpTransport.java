package az.miyav.data.http;

import java.net.URI;
import java.util.Map;

public interface HttpTransport {
    HttpExchange send(String method, URI uri, Map<String, String> headers, String body);

    HttpBinaryExchange sendBinary(String method, URI uri, Map<String, String> headers, byte[] body, String contentType);
}
