package az.miyav.application;

import az.miyav.domain.auth.AuthSession;

/**
 * Telegram verification runs server-side: a Supabase Edge Function holds the bot token, issues the code
 * through the bot and verifies it. Clients never see the bot token or the user's Telegram id.
 */
public interface TelegramVerification {
    void startLink(AuthSession session);

    void confirmCode(AuthSession session, String code);

    /** False until the server-side verification function is deployed. */
    default boolean available() {
        return true;
    }
}
