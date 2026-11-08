package az.miyav.android.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import az.miyav.android.R;
import az.miyav.android.databinding.ViewPublisherCardBinding;
import az.miyav.domain.user.PublicProfile;

import java.util.Optional;

/** Binds the shared publisher / walker profile card used by listing details and public profiles. */
final class PublisherCards {
    private PublisherCards() {
    }

    static void bind(Fragment fragment, ViewPublisherCardBinding card, PublicProfile profile, boolean self,
                     String whatsAppMessage, boolean linkToProfile) {
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
        boolean contactable = !self && digits.isPresent();
        card.contactRow.setVisibility(contactable ? View.VISIBLE : View.GONE);
        card.contactUnavailable.setVisibility(!self && digits.isEmpty() ? View.VISIBLE : View.GONE);
        digits.ifPresent(number -> {
            card.whatsappButton.setOnClickListener(view -> Contact.whatsApp(context, number, whatsAppMessage));
            card.callButton.setOnClickListener(view -> Contact.call(context, number));
        });
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
