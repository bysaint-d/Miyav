package az.miyav.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.R;
import az.miyav.android.databinding.ItemWalkListingBinding;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Walk requests on Discover: who, when, where and the price per hour with the total. */
final class WalkListingAdapter extends RecyclerView.Adapter<WalkListingAdapter.Holder> {
    private final List<WalkListing> listings = new ArrayList<>();
    private final Consumer<WalkListing> onClick;
    private boolean showStatus;

    WalkListingAdapter(Consumer<WalkListing> onClick) {
        this.onClick = onClick;
    }

    void showStatus(boolean showStatus) {
        this.showStatus = showStatus;
    }

    void replace(List<WalkListing> items) {
        listings.clear();
        listings.addAll(items);
        notifyDataSetChanged();
    }

    void append(List<WalkListing> items) {
        int start = listings.size();
        listings.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    boolean isEmpty() {
        return listings.isEmpty();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWalkListingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        WalkListing listing = listings.get(position);
        WalkListingDetails details = listing.details();
        Context context = holder.itemView.getContext();
        holder.binding.speciesBadge.setText(Labels.speciesEmoji(details.species()));
        holder.binding.speciesBadge.setBackgroundTintList(ColorStateList.valueOf(Labels.speciesColor(context, details.species())));
        holder.binding.titleView.setText(details.petName());
        holder.binding.timeView.setText(Labels.date(details.startsAt()) + "  ·  " + Labels.duration(context, details.durationMinutes()));
        holder.binding.placeView.setText(Labels.walkPlace(details));
        holder.binding.priceView.setText(context.getString(R.string.wl_per_hour, Labels.money(listing.effectivePricePerHour())));
        holder.binding.totalView.setText(context.getString(R.string.wl_total, Labels.money(listing.effectiveTotal()),
                Labels.duration(context, details.durationMinutes())));
        holder.binding.statusView.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        holder.binding.statusView.setText(Labels.status(context, listing.status()));
        holder.itemView.setOnClickListener(view -> onClick.accept(listing));
    }

    @Override
    public int getItemCount() {
        return listings.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemWalkListingBinding binding;

        Holder(ItemWalkListingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
