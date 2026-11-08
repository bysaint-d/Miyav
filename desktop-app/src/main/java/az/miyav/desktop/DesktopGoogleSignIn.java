package az.miyav.desktop;

import az.miyav.application.GoogleSignIn;
import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ConfigurationException;
import az.miyav.domain.auth.AuthSession;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Google sign-in for the desktop client: a one-shot HTTP listener on the loopback interface receives the
 * PKCE code, which is exchanged with the verifier that never leaves this process. The redirect URL
 * {@code http://127.0.0.1:47821/callback} must be listed in Supabase Auth.
 */
final class DesktopGoogleSignIn {
    static final int PORT = 47821;
    private final DesktopGraph graph;
    private final CompletableFuture<String> code = new CompletableFuture<>();

    DesktopGoogleSignIn(DesktopGraph graph) {
        this.graph = graph;
    }

    /** Stops waiting for the browser, for example when the user closes the sign-in dialog. */
    void cancel() {
        code.complete("");
    }

    /** Blocks the calling worker thread until the browser returns or three minutes pass. */
    AuthSession run(Consumer<String> openBrowser) {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0);
        } catch (IOException exception) {
            throw new ConfigurationException("auth.google_unavailable");
        }
        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String value = parameter(query, "code");
            byte[] page = ("<!doctype html><meta charset=utf-8><title>MIYAV</title>"
                    + "<body style=\"font-family:Segoe UI,Arial;background:#0e0e0e;color:#f7f7f5;display:grid;place-items:center;height:100vh\">"
                    + "<h2>MIYAV ✓</h2></body>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, page.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(page);
            }
            code.complete(value);
        });
        server.start();
        try {
            GoogleSignIn signIn = graph.auth().beginGoogle("http://127.0.0.1:" + PORT + "/callback");
            openBrowser.accept(signIn.authorizeUri().toString());
            String authCode = code.get(3, TimeUnit.MINUTES);
            if (authCode.isEmpty()) {
                throw new AccessDeniedException("auth.link_invalid");
            }
            return graph.session().completeGoogle(authCode, signIn.verifier());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AccessDeniedException("auth.link_invalid");
        } catch (ExecutionException | TimeoutException exception) {
            throw new AccessDeniedException("auth.link_invalid");
        } finally {
            server.stop(0);
        }
    }

    private static String parameter(String query, String name) {
        if (query == null) {
            return "";
        }
        for (String pair : query.split("&")) {
            int split = pair.indexOf('=');
            if (split > 0 && pair.substring(0, split).equals(name)) {
                return URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
