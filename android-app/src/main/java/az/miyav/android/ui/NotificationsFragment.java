package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

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
            notification.conversationId().ifPresent(room -> Ui.openChat(this, room.toString(), null, null, null));
            markRead(notification);
        });
        binding.itemList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.itemList.setAdapter(adapter);
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
