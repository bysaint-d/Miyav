package az.miyav.android.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import az.miyav.android.R;

import java.util.Locale;

/** External actions: WhatsApp, phone, share and map. Numbers come only from a profile owner who opted in. */
final class Contact {
    private Contact() {
    }

    static Uri whatsAppUri(String internationalDigits, String message) {
        return Uri.parse("https://wa.me/" + internationalDigits + "?text=" + Uri.encode(message));
    }

    static void whatsApp(Context context, String internationalDigits, String message) {
        launch(context, new Intent(Intent.ACTION_VIEW, whatsAppUri(internationalDigits, message)));
    }

    static void call(Context context, String internationalDigits) {
        launch(context, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + internationalDigits)));
    }

    static void share(Context context, String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        launch(context, Intent.createChooser(send, context.getString(R.string.share_listing)));
    }

    static void map(Context context, double latitude, double longitude, String label) {
        String coordinates = String.format(Locale.US, "%.3f,%.3f", latitude, longitude);
        Uri uri = Uri.parse("geo:" + coordinates + "?q=" + coordinates + "(" + Uri.encode(label) + ")");
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException exception) {
            launch(context, new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.openstreetmap.org/?mlat=" + latitude + "&mlon=" + longitude + "#map=16/" + latitude + "/" + longitude)));
        }
    }

    /** Turn-by-turn directions in Google Maps (the app when installed, otherwise the website). */
    static void directions(Context context, double latitude, double longitude) {
        Uri uri = Uri.parse(String.format(Locale.US,
                "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", latitude, longitude));
        Intent maps = new Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps");
        try {
            context.startActivity(maps);
        } catch (ActivityNotFoundException exception) {
            launch(context, new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    /** Dials a number exactly as published by the place. */
    static void dial(Context context, String phone) {
        launch(context, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.replaceAll("[^0-9+]", ""))));
    }

    static void web(Context context, String url) {
        launch(context, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    static String listingLink(java.util.UUID listingId) {
        return "az.miyav.native://listing/" + listingId;
    }

    private static void launch(Context context, Intent intent) {
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(context, R.string.contact_no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
