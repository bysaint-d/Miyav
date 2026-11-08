package az.miyav.application;

import az.miyav.domain.auth.AuthSession;

public interface TelegramVerification {
    void startLink(AuthSession session);

    void confirmCode(AuthSession session, String code);
}
