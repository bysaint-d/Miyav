package az.miyav.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.ItemPetCardBinding;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Pet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class PetCardAdapter extends RecyclerView.Adapter<PetCardAdapter.Holder> {
    private final List<Pet> pets = new ArrayList<>();
    private final MiyavApplication app;
    private final boolean showStatus;
    private final Consumer<Pet> onClick;
    private Double originLatitude;
    private Double originLongitude;

    PetCardAdapter(MiyavApplication app, boolean showStatus, Consumer<Pet> onClick) {
        this.app = app;
        this.showStatus = showStatus;
        this.onClick = onClick;
    }

    /** Shows distance badges from this point; pass null to hide them. */
    void origin(Double latitude, Double longitude) {
        this.originLatitude = latitude;
        this.originLongitude = longitude;
    }

    void replace(List<Pet> items) {
        pets.clear();
        pets.addAll(items);
        notifyDataSetChanged();
    }

    void append(List<Pet> items) {
        int start = pets.size();
        pets.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    boolean isEmpty() {
        return pets.isEmpty();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPetCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Pet pet = pets.get(position);
        Context context = holder.itemView.getContext();
        holder.binding.titleView.setText(pet.name());
        String breed = pet.breed().isEmpty() ? Labels.species(context, pet.species()) : pet.breed();
        holder.binding.subtitleView.setText(breed + " · " + Labels.age(context, pet.ageMonths()));
        holder.binding.placeView.setText(Labels.place(pet));
        holder.binding.speciesBadge.setText(Labels.speciesEmoji(pet.species()));
        holder.binding.speciesBadge.setBackgroundTintList(ColorStateList.valueOf(Labels.speciesColor(context, pet.species())));
        holder.binding.statusChip.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        holder.binding.statusChip.setText(Labels.status(context, pet.status()));
        ListingLocation location = pet.location();
        if (originLatitude != null && location != null) {
            double meters = ListingLocation.distanceMeters(originLatitude, originLongitude, location.latitude(), location.longitude());
            holder.binding.distanceBadge.setText(Labels.distance(context, meters));
            holder.binding.distanceBadge.setVisibility(View.VISIBLE);
        } else {
            holder.binding.distanceBadge.setVisibility(View.GONE);
        }
        String first = pet.photoPaths().isEmpty() ? null : pet.photoPaths().get(0);
        app.photos().listing(holder.binding.photoView, first, R.drawable.ic_paw);
        holder.itemView.setOnClickListener(view -> onClick.accept(pet));
    }

    @Override
    public int getItemCount() {
        return pets.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemPetCardBinding binding;

        Holder(ItemPetCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setClipToOutline(true);
        }
    }
}
