package az.miyav.android.ui;

import az.miyav.android.R;

public final class Codes {
    private Codes() {
    }

    public static int text(String code) {
        if (code == null) {
            return R.string.error_generic;
        }
        return switch (code) {
            case "auth.failed" -> R.string.auth_failed;
            case "auth.suspended" -> R.string.auth_suspended;
            case "auth.confirmation_required" -> R.string.auth_confirmation_required;
            case "auth.profile_unavailable" -> R.string.auth_profile_unavailable;
            case "auth.unavailable" -> R.string.auth_unavailable;
            case "auth.session_expired" -> R.string.auth_session_expired;
            case "auth.link_invalid" -> R.string.auth_link_invalid;
            case "auth.same_password" -> R.string.auth_same_password;
            case "auth.current_password" -> R.string.auth_current_password;
            case "auth.user_exists" -> R.string.auth_user_exists;
            case "auth.rate_limited" -> R.string.auth_rate_limited;
            case "auth.reset_sent" -> R.string.auth_reset_sent;
            case "auth.check_email" -> R.string.auth_check_email;
            case "config.missing" -> R.string.config_missing;
            case "config.rejected" -> R.string.config_rejected;
            case "network.unavailable" -> R.string.network_unavailable;
            case "validation.email" -> R.string.validation_email;
            case "validation.password" -> R.string.validation_password;
            case "validation.name" -> R.string.validation_name;
            case "validation.profile" -> R.string.validation_profile;
            case "validation.pet" -> R.string.validation_pet;
            case "validation.city" -> R.string.validation_city;
            case "adoption.own_listing" -> R.string.adoption_own_listing;
            case "adoption.listing_unavailable" -> R.string.adoption_listing_unavailable;
            case "adoption.already_accepted" -> R.string.adoption_already_accepted;
            case "adoption.duplicate" -> R.string.adoption_duplicate;
            case "adoption.not_pending" -> R.string.adoption_not_pending;
            case "adoption.pet_mismatch" -> R.string.adoption_pet_mismatch;
            case "access.admin" -> R.string.access_admin;
            case "access.owner" -> R.string.access_owner;
            case "schema.not_ready" -> R.string.schema_not_ready;
            case "location.denied" -> R.string.location_denied;
            case "location.unavailable" -> R.string.location_unavailable;
            case "location.out_of_area" -> R.string.location_out_of_area;
            case "media.too_large" -> R.string.media_too_large;
            case "pet.photo_required" -> R.string.pet_photo_required;
            case "pet.photo_limit" -> R.string.pet_photo_limit;
            case "sync.offline" -> R.string.sync_offline;
            case "validation.bio" -> R.string.validation_bio;
            case "validation.phone" -> R.string.validation_phone;
            case "validation.phone_required" -> R.string.validation_phone_required;
            case "pet.location_required" -> R.string.pet_location_required;
            case "walk.no_session" -> R.string.walk_no_session;
            case "walk.species_required" -> R.string.walk_species_required;
            case "walk.text" -> R.string.walk_text;
            default -> R.string.error_generic;
        };
    }
}
