package az.miyav.android.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentInboxBinding;
import az.miyav.android.databinding.ItemConversationBinding;
import az.miyav.domain.messaging.Conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The user's chats, newest first, with unread markers. */
public class InboxFragment extends Fragment {
    private FragmentInboxBinding binding;
    private Adapter adapter;
    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentInboxBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        adapter = new Adapter(conversation -> Ui.openChat(this, conversation.id().toString(), null,
                conversation.otherUserId().toString(), conversation.otherName()));
        binding.itemList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.itemList.setAdapter(adapter);
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnChildScrollUpCallback((parent, child) -> binding.itemList.canScrollVertically(-1));
        binding.refresh.setOnRefreshListener(this::load);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
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
            return app.messaging().inbox(app.auth().requireSession());
        }, items -> {
            loaded = true;
            binding.refresh.setRefreshing(false);
            adapter.replace(items);
            if (items.isEmpty()) {
                States.message(binding.state, R.string.inbox_empty, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> {
            binding.refresh.setRefreshing(false);
            States.message(binding.state, Codes.text(code), this::load);
        });
    }

    static String preview(Context context, Conversation conversation) {
        String text = conversation.lastPrice()
                .map(price -> context.getString(R.string.chat_price_preview, Labels.money(price)))
                .orElse(conversation.lastBody().isEmpty() ? context.getString(R.string.chat_photo) : conversation.lastBody());
        return conversation.lastFromMe() ? context.getString(R.string.chat_you, text) : text;
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final List<Conversation> items = new ArrayList<>();
        private final Consumer<Conversation> onClick;

        Adapter(Consumer<Conversation> onClick) {
            this.onClick = onClick;
        }

        void replace(List<Conversation> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemConversationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Conversation conversation = items.get(position);
            Context context = holder.itemView.getContext();
            String name = conversation.otherName().isEmpty() ? context.getString(R.string.wl_unknown_user) : conversation.otherName();
            holder.binding.initialView.setText(name.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
            holder.binding.nameView.setText(name);
            holder.binding.nameView.setTypeface(null, conversation.unread() ? Typeface.BOLD : Typeface.NORMAL);
            holder.binding.previewView.setText(preview(context, conversation));
            holder.binding.timeView.setText(DateUtils.getRelativeTimeSpanString(conversation.lastMessageAt().toEpochMilli(),
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
            holder.binding.unreadDot.setVisibility(conversation.unread() ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(view -> onClick.accept(conversation));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemConversationBinding binding;

            Holder(ItemConversationBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
