package az.miyav.data.http;

public final class HttpExchange {
    private final int status;
    private final String body;

    public HttpExchange(int status, String body) {
        this.status = status;
        this.body = body == null ? "" : body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }
}
