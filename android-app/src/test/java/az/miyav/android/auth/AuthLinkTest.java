package az.miyav.android.auth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AuthLinkTest {
    @Test
    public void recoveryFragmentIsParsedAndDecoded() {
        AuthLink link = AuthLink.parse(
                "access_token=abc.def.ghi&expires_in=3600&refresh_token=r%2Bt&token_type=bearer&type=recovery",
                null);
        assertEquals(AuthLink.Kind.RECOVERY, link.kind());
        assertEquals("abc.def.ghi", link.accessToken());
        assertEquals("r+t", link.refreshToken());
        assertEquals(3600, link.expiresIn());
    }

    @Test
    public void signupConfirmationIsNotRecovery() {
        AuthLink link = AuthLink.parse("access_token=a&refresh_token=b&expires_in=10&type=signup", "");
        assertEquals(AuthLink.Kind.SIGNUP, link.kind());
    }

    @Test
    public void googleRedirectCarriesOnlyAOneTimeCode() {
        AuthLink link = AuthLink.parse(null, "code=7f1c-42");
        assertEquals(AuthLink.Kind.CODE, link.kind());
        assertEquals("7f1c-42", link.code());
        assertEquals("", link.accessToken());
        assertEquals(AuthLink.Kind.ERROR, AuthLink.parse(null, "error=access_denied&code=x").kind());
    }

    @Test
    public void errorsAndEmptyLinksAreRejected() {
        assertEquals(AuthLink.Kind.ERROR, AuthLink.parse("error=access_denied&error_code=otp_expired", null).kind());
        assertEquals("otp_expired", AuthLink.parse(null, "error_code=otp_expired").errorCode());
        assertEquals(AuthLink.Kind.UNKNOWN, AuthLink.parse("", "").kind());
        assertEquals(AuthLink.Kind.UNKNOWN, AuthLink.parse("type=recovery", null).kind());
        assertEquals(0, AuthLink.parse("access_token=a&expires_in=x", null).expiresIn());
    }
}
