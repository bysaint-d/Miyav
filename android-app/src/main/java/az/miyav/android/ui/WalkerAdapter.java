package az.miyav.android.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.databinding.ItemWalkerBinding;
import az.miyav.domain.walk.Walker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class WalkerAdapter extends RecyclerView.Adapter<WalkerAdapter.Holder> {
    private final List<Walker> walkers = new ArrayList<>();
    private final MiyavApplication app;
    private final Consumer<Walker> onClick;

    WalkerAdapter(MiyavApplication app, Consumer<Walker> onClick) {
        this.app = app;
        this.onClick = onClick;
    }

    void replace(List<Walker> items) {
        walkers.clear();
        walkers.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWalkerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Walker walker = walkers.get(position);
        Context context = holder.itemView.getContext();
        holder.binding.nameView.setText(walker.fullName());
        holder.binding.petView.setText(petLine(context, walker));
        holder.binding.distanceView.setText(context.getString(az.miyav.android.R.string.walk_distance,
                Labels.distance(context, walker.distanceMeters())));
        app.photos().avatar(holder.binding.avatarView, walker.avatarPath());
        holder.itemView.setOnClickListener(view -> onClick.accept(walker));
    }

    static String petLine(Context context, Walker walker) {
        String species = Labels.species(context, walker.species());
        String emoji = Labels.speciesEmoji(walker.species());
        return walker.petName().isEmpty() ? emoji + " " + species : emoji + " " + walker.petName() + " · " + species;
    }

    @Override
    public int getItemCount() {
        return walkers.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemWalkerBinding binding;

        Holder(ItemWalkerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
