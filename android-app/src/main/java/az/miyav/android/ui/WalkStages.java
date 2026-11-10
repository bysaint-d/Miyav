package az.miyav.android.ui;

import android.content.Context;

import androidx.core.content.ContextCompat;

import az.miyav.android.R;
import az.miyav.domain.walk.MyWalks;

/** How each walk stage looks for the member: a short badge, an icon and the badge colours. */
final class WalkStages {
    private WalkStages() {
    }

    static String badge(Context context, MyWalks.Stage stage, int pendingOffers) {
        return switch (stage) {
            case WAITING_FOR_OFFERS -> context.getString(R.string.walk_stage_waiting);
            case OFFERS_TO_REVIEW -> context.getResources().getQuantityString(R.plurals.walk_stage_review, pendingOffers, pendingOffers);
            case OFFER_SENT -> context.getString(R.string.walk_stage_sent);
            case AGREED -> context.getString(R.string.walk_stage_agreed);
            case OFFER_DECLINED -> context.getString(R.string.walk_stage_declined);
            case OFFER_WITHDRAWN -> context.getString(R.string.walk_stage_withdrawn);
            case COMPLETED -> context.getString(R.string.walk_stage_completed);
            case CANCELLED -> context.getString(R.string.walk_stage_cancelled);
            case EXPIRED -> context.getString(R.string.walk_stage_expired);
        };
    }

    static String icon(MyWalks.Stage stage) {
        return switch (stage) {
            case WAITING_FOR_OFFERS -> "⏳";
            case OFFERS_TO_REVIEW -> "📬";
            case OFFER_SENT -> "📤";
            case AGREED -> "✅";
            case COMPLETED -> "🏁";
            case EXPIRED -> "⌛";
            default -> "✖";
        };
    }

    static int fill(Context context, MyWalks.Stage stage) {
        return ContextCompat.getColor(context, switch (stage) {
            case OFFERS_TO_REVIEW -> R.color.brand_sun;
            case WAITING_FOR_OFFERS, OFFER_SENT -> R.color.pastel_sky;
            case AGREED -> R.color.pastel_success;
            case COMPLETED -> R.color.pastel_mint;
            default -> R.color.surface_muted;
        });
    }

    static int ink(Context context, MyWalks.Stage stage) {
        return ContextCompat.getColor(context, switch (stage) {
            case AGREED -> R.color.success;
            case OFFER_DECLINED, OFFER_WITHDRAWN, CANCELLED, EXPIRED -> R.color.text_secondary;
            default -> R.color.text_primary;
        });
    }

    /** Softer background for the large card on the walk screen. */
    static int card(Context context, MyWalks.Stage stage) {
        return ContextCompat.getColor(context, switch (stage) {
            case OFFERS_TO_REVIEW -> R.color.pastel_butter;
            case WAITING_FOR_OFFERS, OFFER_SENT -> R.color.pastel_sky;
            case AGREED -> R.color.pastel_success;
            case COMPLETED -> R.color.pastel_mint;
            default -> R.color.surface_muted;
        });
    }
}
