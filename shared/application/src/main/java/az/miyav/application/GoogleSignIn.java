package az.miyav.application;

import java.net.URI;

/** A started Google sign-in: the URL to open and the PKCE verifier to keep private until the redirect. */
public final class GoogleSignIn {
    private final URI authorizeUri;
    private final String verifier;

    public GoogleSignIn(URI authorizeUri, String verifier) {
        this.authorizeUri = authorizeUri;
        this.verifier = verifier;
    }

    public URI authorizeUri() {
        return authorizeUri;
    }

    public String verifier() {
        return verifier;
    }

    @Override
    public String toString() {
        return "GoogleSignIn{" + authorizeUri.getHost() + "}";
    }
}
