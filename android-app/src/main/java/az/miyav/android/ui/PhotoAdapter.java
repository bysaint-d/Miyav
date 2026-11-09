package az.miyav.android.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.ItemPhotoBinding;
import az.miyav.android.databinding.ItemPhotoThumbBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Full-width carousel when {@code onRemove} is null, removable thumbnails otherwise. */
final class PhotoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<String> paths = new ArrayList<>();
    private final MiyavApplication app;
    private final Consumer<String> onRemove;
    private IntConsumer onOpen;

    PhotoAdapter(MiyavApplication app, Consumer<String> onRemove) {
        this.app = app;
        this.onRemove = onRemove;
    }

    /** Carousel only: called with the tapped photo's position. */
    void onOpen(IntConsumer listener) {
        this.onOpen = listener;
    }

    void replace(List<String> items) {
        paths.clear();
        paths.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (onRemove == null) {
            ItemPhotoBinding binding = ItemPhotoBinding.inflate(inflater, parent, false);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                binding.backdropView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        48f, 48f, android.graphics.Shader.TileMode.CLAMP));
            } else {
                binding.backdropView.setAlpha(0.25f);
            }
            return new GalleryHolder(binding);
        }
        ItemPhotoThumbBinding binding = ItemPhotoThumbBinding.inflate(inflater, parent, false);
        return new ThumbHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        String path = paths.get(position);
        if (holder instanceof ThumbHolder thumb) {
            app.photos().listing(thumb.binding.photoView, path, R.drawable.ic_paw);
            thumb.binding.removeButton.setOnClickListener(view -> onRemove.accept(path));
            return;
        }
        GalleryHolder gallery = (GalleryHolder) holder;
        app.photos().listingGallery(gallery.binding.photoView, gallery.binding.backdropView, path);
        holder.itemView.setOnClickListener(onOpen == null ? null : view -> {
            int at = holder.getBindingAdapterPosition();
            if (at != RecyclerView.NO_POSITION) {
                onOpen.accept(at);
            }
        });
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    static final class GalleryHolder extends RecyclerView.ViewHolder {
        final ItemPhotoBinding binding;

        GalleryHolder(ItemPhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class ThumbHolder extends RecyclerView.ViewHolder {
        final ItemPhotoThumbBinding binding;

        ThumbHolder(ItemPhotoThumbBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static int visibility(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
