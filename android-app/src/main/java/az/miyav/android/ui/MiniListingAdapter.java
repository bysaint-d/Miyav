package az.miyav.android.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.ItemListingMiniBinding;
import az.miyav.domain.pet.Pet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class MiniListingAdapter extends RecyclerView.Adapter<MiniListingAdapter.Holder> {
    private final List<Pet> pets = new ArrayList<>();
    private final MiyavApplication app;
    private final Consumer<Pet> onClick;

    MiniListingAdapter(MiyavApplication app, Consumer<Pet> onClick) {
        this.app = app;
        this.onClick = onClick;
    }

    void replace(List<Pet> items) {
        pets.clear();
        pets.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemListingMiniBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Pet pet = pets.get(position);
        holder.binding.titleView.setText(pet.name());
        holder.binding.subtitleView.setText(holder.itemView.getContext().getString(R.string.format_icon_label,
                Labels.speciesEmoji(pet.species()), Labels.age(holder.itemView.getContext(), pet.ageMonths())));
        String first = pet.photoPaths().isEmpty() ? null : pet.photoPaths().get(0);
        app.photos().listing(holder.binding.photoView, first, R.drawable.ic_paw);
        holder.itemView.setOnClickListener(view -> onClick.accept(pet));
    }

    @Override
    public int getItemCount() {
        return pets.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemListingMiniBinding binding;

        Holder(ItemListingMiniBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
