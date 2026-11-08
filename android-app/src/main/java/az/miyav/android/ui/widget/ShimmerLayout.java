package az.miyav.android.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import az.miyav.android.R;

/**
 * Draws a soft highlight band across its skeleton children. The band is composited only onto pixels the
 * children already drew, so it follows their rounded shapes. Runs only while visible and attached.
 */
public final class ShimmerLayout extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private LinearGradient gradient;
    private ValueAnimator animator;
    private float progress;

    public ShimmerLayout(@NonNull Context context) {
        this(context, null);
    }

    public ShimmerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        int highlight = ContextCompat.getColor(getContext(), R.color.skeleton_highlight);
        int transparent = highlight & 0x00FFFFFF;
        float band = Math.max(width, 1) * 0.45f;
        gradient = new LinearGradient(0, 0, band, 0,
                new int[]{transparent, highlight, transparent}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (gradient == null || getWidth() == 0) {
            super.dispatchDraw(canvas);
            return;
        }
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.dispatchDraw(canvas);
        float band = getWidth() * 0.45f;
        float offset = -band + (getWidth() + band * 2) * progress;
        matrix.setTranslate(offset, 0);
        gradient.setLocalMatrix(matrix);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        canvas.restoreToCount(save);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimation();
    }

    private void updateAnimation() {
        if (isAttachedToWindow() && isShown()) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private void startAnimation() {
        if (animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1300);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
