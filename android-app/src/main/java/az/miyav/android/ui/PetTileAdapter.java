package az.miyav.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.ItemPetTileBinding;
import az.miyav.domain.pet.PetSpecies;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Round pet tiles: own pets to tick before a walk, or the pets a nearby walker is out with. An optional
 * last tile adds a new pet.
 */
final class PetTileAdapter extends RecyclerView.Adapter<PetTileAdapter.Holder> {
    static final class Tile {
        final Object key;
        final String name;
        final String meta;
        final PetSpecies species;
        final String photoPath;
        final boolean fromJob;

        Tile(Object key, String name, String meta, PetSpecies species, String photoPath, boolean fromJob) {
            this.key = key;
            this.name = name;
            this.meta = meta;
            this.species = species;
            this.photoPath = photoPath;
            this.fromJob = fromJob;
        }
    }

    private final MiyavApplication app;
    private final List<Tile> tiles = new ArrayList<>();
    private final Set<Object> selected = new LinkedHashSet<>();
    private final Consumer<Tile> onClick;
    private final Runnable onAdd;

    /** {@code onClick} null makes the tiles read-only; {@code onAdd} null hides the add tile. */
    PetTileAdapter(MiyavApplication app, Consumer<Tile> onClick, Runnable onAdd) {
        this.app = app;
        this.onClick = onClick;
        this.onAdd = onAdd;
    }

    void replace(List<Tile> next) {
        tiles.clear();
        tiles.addAll(next);
        notifyDataSetChanged();
    }

    void setSelected(Set<Object> keys) {
        selected.clear();
        selected.addAll(keys);
        notifyDataSetChanged();
    }

    boolean isEmpty() {
        return tiles.isEmpty();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPetTileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Context context = holder.itemView.getContext();
        ItemPetTileBinding view = holder.binding;
        if (position == tiles.size()) {
            view.photoView.setTag(R.id.photo_tag, null);
            view.photoView.setImageDrawable(null);
            view.photoView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_muted)));
            view.photoView.setStrokeWidth(0);
            view.emojiView.setVisibility(View.VISIBLE);
            view.emojiView.setText("+");
            view.emojiView.setTextColor(ContextCompat.getColor(context, R.color.brand_coral));
            view.checkView.setVisibility(View.GONE);
            view.jobBadge.setVisibility(View.GONE);
            view.nameView.setText(R.string.pet_new);
            view.metaView.setText("");
            holder.itemView.setOnClickListener(v -> onAdd.run());
            return;
        }
        Tile tile = tiles.get(position);
        boolean chosen = selected.contains(tile.key);
        view.photoView.setBackgroundTintList(ColorStateList.valueOf(Labels.speciesColor(context, tile.species)));
        view.photoView.setStrokeWidth(chosen ? context.getResources().getDisplayMetrics().density * 3 : 0);
        if (tile.photoPath != null) {
            view.emojiView.setVisibility(View.GONE);
            app.photos().pet(view.photoView, tile.photoPath);
        } else {
            view.photoView.setTag(R.id.photo_tag, null);
            view.photoView.setImageDrawable(null);
            view.emojiView.setVisibility(View.VISIBLE);
            view.emojiView.setText(Labels.speciesEmoji(tile.species));
            view.emojiView.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }
        view.checkView.setVisibility(chosen ? View.VISIBLE : View.GONE);
        view.jobBadge.setVisibility(tile.fromJob ? View.VISIBLE : View.GONE);
        view.nameView.setText(tile.name);
        view.metaView.setText(tile.meta);
        holder.itemView.setClickable(onClick != null);
        holder.itemView.setOnClickListener(onClick == null ? null : v -> onClick.accept(tile));
    }

    @Override
    public int getItemCount() {
        return tiles.size() + (onAdd == null ? 0 : 1);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemPetTileBinding binding;

        Holder(ItemPetTileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
