package az.miyav.android.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import az.miyav.android.BuildConfig;
import az.miyav.android.R;

import java.io.File;

/** OpenStreetMap setup and marker drawing shared by the picker, the listing preview and Gəzinti. */
public final class MapSupport {
    /** Initial camera only (centre of Bakı) — never saved as anyone's location. */
    public static final GeoPoint DEFAULT_CAMERA = new GeoPoint(40.4093, 49.8671);

    private MapSupport() {
    }

    public static void init(Context context) {
        IConfigurationProvider configuration = Configuration.getInstance();
        configuration.setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        File base = new File(context.getCacheDir(), "osmdroid");
        configuration.setOsmdroidBasePath(base);
        configuration.setOsmdroidTileCache(new File(base, "tiles"));
    }

    public static void style(MapView map, boolean interactive) {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(interactive);
        map.setClickable(interactive);
        map.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(6.0);
        map.setMaxZoomLevel(19.0);
        if (!interactive) {
            map.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                    view.performClick();
                }
                return true;
            });
        }
    }

    /** Round marker with a species emoji, optionally ringed in the brand colour. */
    public static Drawable emojiMarker(Context context, String emoji, int fillColor, boolean highlighted) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(48 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ContextCompat.getColor(context, highlighted ? R.color.brand_coral : R.color.surface));
        canvas.drawOval(new RectF(0, 0, size, size), paint);
        paint.setColor(fillColor);
        float inset = 4 * density;
        canvas.drawOval(new RectF(inset, inset, size - inset, size - inset), paint);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(22 * density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT);
        Paint.FontMetrics metrics = text.getFontMetrics();
        canvas.drawText(emoji, size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, text);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    /** Circular avatar marker with a white ring. */
    public static Drawable avatarMarker(Context context, Bitmap avatar) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(48 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ContextCompat.getColor(context, R.color.surface));
        canvas.drawOval(new RectF(0, 0, size, size), paint);
        float inset = 3 * density;
        RectF inner = new RectF(inset, inset, size - inset, size - inset);
        Bitmap scaled = Bitmap.createScaledBitmap(avatar, Math.round(inner.width()), Math.round(inner.height()), true);
        Paint image = new Paint(Paint.ANTI_ALIAS_FLAG);
        image.setShader(new android.graphics.BitmapShader(scaled, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        canvas.save();
        canvas.translate(inset, inset);
        canvas.drawOval(new RectF(0, 0, inner.width(), inner.height()), image);
        canvas.restore();
        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
