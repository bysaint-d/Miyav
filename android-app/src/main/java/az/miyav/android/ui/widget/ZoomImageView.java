package az.miyav.android.ui.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Shows the whole image and lets the user pinch, double-tap and drag to look closer. Gestures apply only in
 * {@link ScaleType#MATRIX}; other scale types (the loading placeholder) behave like a plain image view.
 * While zoomed it keeps the touch stream from its parent so a pager does not swipe away mid-pan.
 */
public class ZoomImageView extends AppCompatImageView {
    private static final float MAX_SCALE = 4f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;
    private final Matrix base = new Matrix();
    private final Matrix zoom = new Matrix();
    private final Matrix drawn = new Matrix();
    private final RectF bounds = new RectF();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private boolean ready;
    private boolean tapped;

    public ZoomImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        scaler = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                scaleBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent first, @NonNull MotionEvent event, float dx, float dy) {
                if (!zoomed()) {
                    return false;
                }
                zoom.postTranslate(-dx, -dy);
                apply();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent event) {
                if (zoomed()) {
                    reset();
                } else {
                    scaleBy(DOUBLE_TAP_SCALE, event.getX(), event.getY());
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent event) {
                tapped = true;
                return true;
            }
        });
        ready = true;
        reset();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        reset();
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        super.setScaleType(scaleType);
        reset();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        reset();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!zoomable()) {
            return super.onTouchEvent(event);
        }
        scaler.onTouchEvent(event);
        gestures.onTouchEvent(event);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(zoomed() || event.getPointerCount() > 1);
        }
        if (tapped) {
            tapped = false;
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Back to the whole image, centred. */
    public void reset() {
        if (!ready) {
            return;
        }
        zoom.reset();
        Drawable drawable = getDrawable();
        if (!zoomable() || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        base.setRectToRect(new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()),
                new RectF(0, 0, getWidth(), getHeight()), Matrix.ScaleToFit.CENTER);
        apply();
    }

    private boolean zoomable() {
        Drawable drawable = getDrawable();
        return getScaleType() == ScaleType.MATRIX && drawable != null
                && drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0;
    }

    private boolean zoomed() {
        zoom.getValues(values);
        return values[Matrix.MSCALE_X] > 1.01f;
    }

    private void scaleBy(float factor, float focusX, float focusY) {
        zoom.getValues(values);
        float current = values[Matrix.MSCALE_X];
        float target = Math.max(1f, Math.min(MAX_SCALE, current * factor));
        float step = target / current;
        zoom.postScale(step, step, focusX, focusY);
        apply();
    }

    /** Keeps the image on screen: centred along an axis it does not fill, edge-bound along one it overflows. */
    private void apply() {
        Drawable drawable = getDrawable();
        drawn.set(base);
        drawn.postConcat(zoom);
        bounds.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawn.mapRect(bounds);
        float dx = shift(bounds.left, bounds.right, getWidth());
        float dy = shift(bounds.top, bounds.bottom, getHeight());
        zoom.postTranslate(dx, dy);
        drawn.postTranslate(dx, dy);
        setImageMatrix(drawn);
    }

    private static float shift(float start, float end, float size) {
        float length = end - start;
        if (length <= size) {
            return (size - length) / 2f - start;
        }
        if (start > 0) {
            return -start;
        }
        if (end < size) {
            return size - end;
        }
        return 0;
    }
}
