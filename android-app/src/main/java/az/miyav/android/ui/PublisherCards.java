package az.miyav.android.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import az.miyav.android.R;
import az.miyav.android.databinding.ViewPublisherCardBinding;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.user.PublicProfile;

import java.util.Optional;

/** Binds the shared publisher / walker profile card used by listing details and public profiles. */
final class PublisherCards {
    private PublisherCards() {
    }

    /**
     * @param guestContact for guests, the action to resume after sign-in; guests never receive a phone
     *                     number from the server, so they get a sign-in button instead of contact buttons.
     */
    static void bind(Fragment fragment, ViewPublisherCardBinding card, PublicProfile profile, boolean self,
                     String whatsAppMessage, boolean linkToProfile, PendingAction guestContact) {
        Context context = fragment.requireContext();
        card.getRoot().setVisibility(View.VISIBLE);
        card.nameView.setText(profile.fullName());
        card.nameView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, profile.verified() ? R.drawable.ic_verified : 0, 0);
        if (profile.verified()) {
            card.nameView.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.brand_sky)));
        }
        String member = PetDetailFragment.memberSince(context, profile);
        String meta = profile.verified() ? context.getString(R.string.publisher_verified) : "";
        if (!profile.city().isEmpty()) {
            meta = meta.isEmpty() ? profile.city() : meta + "  ·  " + profile.city();
        }
        card.metaView.setText(member.isEmpty() ? meta : (meta.isEmpty() ? member : meta + "\n" + member));
        card.bioView.setVisibility(profile.bio().isEmpty() ? View.GONE : View.VISIBLE);
        card.bioView.setText(profile.bio());
        Ui.app(fragment).photos().avatar(card.avatarView, profile.avatarPath());
        Optional<String> digits = profile.contactDigits();
        boolean guest = guestContact != null;
        boolean contactable = !self && !guest && digits.isPresent();
        card.contactRow.setVisibility(contactable ? View.VISIBLE : View.GONE);
        card.contactUnavailable.setVisibility(!self && !guest && digits.isEmpty() ? View.VISIBLE : View.GONE);
        card.signInContactButton.setVisibility(!self && guest ? View.VISIBLE : View.GONE);
        card.signInContactButton.setOnClickListener(guest ? view -> Gate.require(fragment, guestContact) : null);
        digits.ifPresent(number -> {
            card.whatsappButton.setOnClickListener(view -> Contact.whatsApp(context, number, whatsAppMessage));
            card.callButton.setOnClickListener(view -> Contact.call(context, number));
        });
        card.safetyButton.setVisibility(self ? View.GONE : View.VISIBLE);
        card.safetyButton.setOnClickListener(self ? null
                : view -> SafetyActions.open(fragment, profile.id(), profile.fullName()));
        if (linkToProfile && !self) {
            card.publisherHeader.setOnClickListener(view -> {
                Bundle args = new Bundle();
                args.putString("userId", profile.id().toString());
                Ui.nav(fragment).navigate(R.id.action_global_public_profile, args);
            });
        } else {
            card.publisherHeader.setOnClickListener(null);
        }
    }
}
