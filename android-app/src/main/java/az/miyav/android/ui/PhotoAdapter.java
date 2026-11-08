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

/** Full-width carousel when {@code onRemove} is null, removable thumbnails otherwise. */
final class PhotoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<String> paths = new ArrayList<>();
    private final MiyavApplication app;
    private final Consumer<String> onRemove;

    PhotoAdapter(MiyavApplication app, Consumer<String> onRemove) {
        this.app = app;
        this.onRemove = onRemove;
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
            return new RecyclerView.ViewHolder(binding.getRoot()) {
            };
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
        app.photos().listing((android.widget.ImageView) holder.itemView, path, R.drawable.ic_paw);
    }

    @Override
    public int getItemCount() {
        return paths.size();
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
