package az.miyav.data.http;

public final class HttpBinaryExchange {
    private final int status;
    private final byte[] body;

    public HttpBinaryExchange(int status, byte[] body) {
        this.status = status;
        this.body = body == null ? new byte[0] : body;
    }

    public int status() {
        return status;
    }

    public byte[] body() {
        return body;
    }
}
