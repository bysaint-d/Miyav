package az.miyav.android.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.imageview.ShapeableImageView;

/** Grid tile photo: always 4:3, so tiles in a row line up like on classified sites. */
public class ListingPhotoView extends ShapeableImageView {
    private static final float RATIO = 3f / 4f;

    public ListingPhotoView(@NonNull Context context) {
        super(context);
    }

    public ListingPhotoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, Math.round(width * RATIO));
    }
}
