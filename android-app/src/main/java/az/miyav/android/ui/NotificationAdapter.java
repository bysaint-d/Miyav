package az.miyav.android.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.databinding.ItemNotificationBinding;
import az.miyav.domain.notification.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.Holder> {
    private final List<Notification> rows = new ArrayList<>();
    private final Consumer<Notification> onClick;

    NotificationAdapter(Consumer<Notification> onClick) {
        this.onClick = onClick;
    }

    void replace(List<Notification> items) {
        rows.clear();
        rows.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Notification notification = rows.get(position);
        holder.binding.titleView.setText(notification.title());
        holder.binding.bodyView.setText(notification.body());
        holder.binding.dateView.setText(Labels.date(notification.createdAt()));
        holder.binding.unreadDot.setVisibility(notification.unread() ? View.VISIBLE : View.INVISIBLE);
        holder.itemView.setOnClickListener(view -> onClick.accept(notification));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemNotificationBinding binding;

        Holder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
