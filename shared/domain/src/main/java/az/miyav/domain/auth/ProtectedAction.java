package az.miyav.domain.auth;

import java.util.Locale;

/**
 * Actions that need a MIYAV account. Everything else (browsing, search, listing details, public
 * profiles, language and legal pages) is open to guests. The server enforces the same split with RLS.
 */
public enum ProtectedAction {
    CREATE_LISTING,
    MANAGE_LISTINGS,
    APPLY_ADOPTION,
    OFFER_WALK,
    APPLICATIONS,
    SYNC_FAVORITES,
    SEND_MESSAGE,
    CONTACT_OWNER,
    EDIT_PROFILE,
    SUBMIT_REPORT,
    NOTIFICATIONS,
    WALK,
    CHANGE_PASSWORD,
    ADMIN;

    /** Message catalog key of the sentence shown in the sign-in sheet for this action. */
    public String promptKey() {
        return "guest.action." + name().toLowerCase(Locale.ROOT);
    }

    public static ProtectedAction fromName(String name) {
        if (name == null) {
            return null;
        }
        for (ProtectedAction action : values()) {
            if (action.name().equals(name)) {
                return action;
            }
        }
        return null;
    }
}
