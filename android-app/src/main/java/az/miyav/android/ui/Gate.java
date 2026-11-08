package az.miyav.android.ui;

import androidx.fragment.app.Fragment;

import az.miyav.android.R;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;

/**
 * The single entry point screens use before a protected action. Browsing never goes through here.
 * When an account is needed the action is remembered and the sign-in sheet opens; after sign-in the
 * originating screen resumes it through {@code app.auth().takePending(...)}.
 */
final class Gate {
    private Gate() {
    }

    static boolean require(Fragment fragment, PendingAction action) {
        switch (Ui.app(fragment).auth().request(action)) {
            case ALLOWED:
                return true;
            case FORBIDDEN:
                Ui.snack(fragment.getView(), R.string.access_admin);
                return false;
            default:
                AuthSheet.show(fragment, action.action());
                return false;
        }
    }

    static boolean require(Fragment fragment, ProtectedAction action) {
        return require(fragment, PendingAction.of(action));
    }

    static int prompt(ProtectedAction action) {
        if (action == null) {
            return R.string.auth_required;
        }
        return switch (action) {
            case CREATE_LISTING -> R.string.guest_action_create_listing;
            case MANAGE_LISTINGS -> R.string.guest_action_manage_listings;
            case APPLY_ADOPTION -> R.string.guest_action_apply_adoption;
            case APPLICATIONS -> R.string.guest_action_applications;
            case SYNC_FAVORITES -> R.string.guest_action_sync_favorites;
            case SEND_MESSAGE -> R.string.guest_action_send_message;
            case CONTACT_OWNER -> R.string.guest_action_contact_owner;
            case EDIT_PROFILE -> R.string.guest_action_edit_profile;
            case SUBMIT_REPORT -> R.string.guest_action_submit_report;
            case NOTIFICATIONS -> R.string.guest_action_notifications;
            case WALK -> R.string.guest_action_walk;
            case OFFER_WALK -> R.string.guest_action_offer_walk;
            case CHANGE_PASSWORD -> R.string.guest_action_change_password;
            case ADMIN -> R.string.guest_action_admin;
        };
    }
}
