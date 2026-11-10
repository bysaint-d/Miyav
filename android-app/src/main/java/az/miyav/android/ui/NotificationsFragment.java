package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentNotificationsBinding;
import az.miyav.domain.notification.Notification;

public class NotificationsFragment extends Fragment {
    private FragmentNotificationsBinding binding;
    private NotificationAdapter adapter;
    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        adapter = new NotificationAdapter(notification -> {
            open(notification);
            markRead(notification);
        }, this::delete);
        binding.itemList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.itemList.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                delete(adapter.at(holder.getBindingAdapterPosition()));
            }
        }).attachToRecyclerView(binding.itemList);
        binding.toolbar.getMenu().add(R.string.notifications_clear)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            confirmClear();
            return true;
        });
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnChildScrollUpCallback((parent, child) -> binding.itemList.canScrollVertically(-1));
        binding.refresh.setOnRefreshListener(this::load);
        load();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (!loaded && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.notificationService().list(app.auth().requireSession());
        }, items -> {
            loaded = true;
            binding.refresh.setRefreshing(false);
            adapter.replace(items);
            if (items.isEmpty()) {
                States.message(binding.state, R.string.notifications_empty, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> {
            binding.refresh.setRefreshing(false);
            States.message(binding.state, Codes.text(code), this::load);
        });
    }

    private void delete(Notification notification) {
        adapter.remove(notification);
        showEmptyIfNeeded();
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            app.notificationService().delete(app.auth().requireSession(), notification.id());
            return Boolean.TRUE;
        }, done -> {
        }, code -> {
            Ui.snack(binding.getRoot(), code);
            load();
        });
    }

    private void confirmClear() {
        if (adapter.isEmpty()) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.notifications_clear_confirm)
                .setPositiveButton(R.string.notifications_clear, (dialog, which) -> Ui.run(this, () -> {
                    MiyavApplication app = Ui.app(this);
                    app.notificationService().deleteAll(app.auth().requireSession());
                    return Boolean.TRUE;
                }, done -> load(), code -> Ui.snack(binding.getRoot(), code)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEmptyIfNeeded() {
        if (adapter.isEmpty()) {
            States.message(binding.state, R.string.notifications_empty, null);
        }
    }

    /** Walk news open the walk, listing news the listing, messages the chat. */
    private void open(Notification notification) {
        Bundle args = new Bundle();
        if (notification.aboutWalk()) {
            args.putString("listingId", notification.listingId().get().toString());
            Ui.nav(this).navigate(R.id.action_global_walk_detail, args);
        } else if (notification.aboutListing()) {
            args.putString("petId", notification.listingId().get().toString());
            Ui.nav(this).navigate(R.id.action_global_detail, args);
        } else if (notification.aboutWallet()) {
            Ui.nav(this).navigate(R.id.action_global_wallet);
        } else {
            notification.conversationId().ifPresent(room -> Ui.openChat(this, room.toString(), null, null, null));
        }
    }

    private void markRead(Notification notification) {
        if (!notification.unread()) {
            return;
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            app.notificationService().markRead(app.auth().requireSession(), notification.id());
            return Boolean.TRUE;
        }, done -> load(), code -> Ui.snack(binding.getRoot(), code));
    }
}
