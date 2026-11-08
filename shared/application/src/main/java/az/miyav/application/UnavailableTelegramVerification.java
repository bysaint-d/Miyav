package az.miyav.application;

import az.miyav.common.error.ConfigurationException;
import az.miyav.domain.auth.AuthSession;

public final class UnavailableTelegramVerification implements TelegramVerification {
    @Override
    public void startLink(AuthSession session) {
        throw new ConfigurationException("telegram.not_deployed");
    }

    @Override
    public void confirmCode(AuthSession session, String code) {
        throw new ConfigurationException("telegram.not_deployed");
    }

    @Override
    public boolean available() {
        return false;
    }
}
