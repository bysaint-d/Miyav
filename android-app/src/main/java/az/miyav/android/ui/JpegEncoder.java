package az.miyav.android.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import az.miyav.common.error.ValidationException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Turns a picked image into an upright JPEG under the storage limit. */
final class JpegEncoder {
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private JpegEncoder() {
    }

    static byte[] fromUri(Context context, Uri uri, int maxEdge) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new ValidationException("validation.pet");
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) {
                throw new ValidationException("validation.pet");
            }
            bitmap = upright(context, uri, scale(bitmap, maxEdge));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int quality = 88;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
            while (output.size() > MAX_BYTES && quality > 50) {
                output.reset();
                quality -= 8;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
            }
            if (output.size() > MAX_BYTES) {
                throw new ValidationException("media.too_large");
            }
            return output.toByteArray();
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ValidationException("validation.pet");
        }
    }

    private static Bitmap upright(Context context, Uri uri, Bitmap bitmap) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return bitmap;
            }
            int orientation = new ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90 -> 90;
                case ExifInterface.ORIENTATION_ROTATE_180 -> 180;
                case ExifInterface.ORIENTATION_ROTATE_270 -> 270;
                default -> 0;
            };
            if (degrees == 0) {
                return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception exception) {
            return bitmap;
        }
    }

    private static Bitmap scale(Bitmap bitmap, int maxEdge) {
        int edge = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (edge <= maxEdge) {
            return bitmap;
        }
        float ratio = maxEdge / (float) edge;
        return Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * ratio), Math.round(bitmap.getHeight() * ratio), true);
    }
}
