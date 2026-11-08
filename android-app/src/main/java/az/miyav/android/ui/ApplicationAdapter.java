package az.miyav.android.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.R;
import az.miyav.android.databinding.ItemApplicationBinding;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ApplicationAdapter extends RecyclerView.Adapter<ApplicationAdapter.Holder> {
    interface Actions {
        void accept(AdoptionApplication application);

        void reject(AdoptionApplication application);

        void cancel(AdoptionApplication application);

        void open(AdoptionApplication application);
    }

    private final List<AdoptionApplication> rows = new ArrayList<>();
    private final Actions actions;
    private Map<java.util.UUID, String> titles = Map.of();
    private boolean incoming;

    ApplicationAdapter(Actions actions) {
        this.actions = actions;
    }

    void replace(List<AdoptionApplication> items, Map<java.util.UUID, String> titles, boolean incoming) {
        rows.clear();
        rows.addAll(items);
        this.titles = titles;
        this.incoming = incoming;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemApplicationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AdoptionApplication application = rows.get(position);
        Context context = holder.itemView.getContext();
        String title = titles.get(application.petId());
        holder.binding.titleView.setText(title == null ? context.getString(R.string.listing_unavailable) : title);
        holder.binding.statusChip.setText(Labels.status(context, application.status()));
        holder.binding.messageView.setText(application.message());
        holder.binding.dateView.setText(Labels.date(application.createdAt()));
        boolean pending = application.status() == ApplicationStatus.PENDING;
        holder.binding.actions.setVisibility(pending ? View.VISIBLE : View.GONE);
        if (incoming) {
            holder.binding.primaryAction.setVisibility(View.VISIBLE);
            holder.binding.primaryAction.setText(R.string.adoption_accept);
            holder.binding.primaryAction.setOnClickListener(view -> actions.accept(application));
            holder.binding.secondaryAction.setText(R.string.adoption_reject);
            holder.binding.secondaryAction.setOnClickListener(view -> actions.reject(application));
        } else {
            holder.binding.primaryAction.setVisibility(View.GONE);
            holder.binding.secondaryAction.setText(R.string.adoption_cancel);
            holder.binding.secondaryAction.setOnClickListener(view -> actions.cancel(application));
        }
        holder.itemView.setOnClickListener(title == null ? null : view -> actions.open(application));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemApplicationBinding binding;

        Holder(ItemApplicationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
