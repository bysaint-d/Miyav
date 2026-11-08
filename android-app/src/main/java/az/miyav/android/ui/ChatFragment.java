package az.miyav.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentChatBinding;
import az.miyav.android.databinding.ItemMessageBinding;
import az.miyav.common.error.AppException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.messaging.Message;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkListingStatus;
import az.miyav.domain.walk.WalkOffer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A private chat, usually about a walk request: the walker can propose an hourly price, which arrives as a
 * price card; the owner accepts the latest price from that walker right in the chat.
 */
public class ChatFragment extends Fragment {
    private static final long POLL_MS = 6000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;
    private FragmentChatBinding binding;
    private MessageAdapter adapter;
    private UUID room;
    private UUID me;
    private UUID listingArg;
    private UUID otherId;
    private String otherName;
    private UUID contextListing;
    private Map<UUID, WalkListing> listings = new HashMap<>();
    private Map<String, WalkOffer> offers = new HashMap<>();
    private UUID lastMessageId;
    private boolean loading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        Bundle args = requireArguments();
        room = uuid(args.getString("conversationId"));
        listingArg = uuid(args.getString("listingId"));
        otherId = uuid(args.getString("withUserId"));
        otherName = args.getString("title", "");
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        binding.toolbar.setOnClickListener(view -> openProfile());
        adapter = new MessageAdapter(this::accept);
        LinearLayoutManager layout = new LinearLayoutManager(requireContext());
        layout.setStackFromEnd(true);
        binding.messageList.setLayoutManager(layout);
        binding.messageList.setAdapter(adapter);
        binding.sendButton.setOnClickListener(view -> send());
        binding.priceButton.setOnClickListener(view -> proposePrice());
        binding.listingBanner.setOnClickListener(view -> {
            if (contextListing != null) {
                Bundle walk = new Bundle();
                walk.putString("listingId", contextListing.toString());
                Ui.nav(this).navigate(R.id.action_global_walk_detail, walk);
            }
        });
        renderTitle();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
        handler.postDelayed(poll, POLL_MS);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(poll);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static String key(UUID listingId, UUID walkerId) {
        return listingId + ":" + walkerId;
    }

    /** Only asks for new messages; a full reload happens when something arrived. */
    private void poll() {
        handler.postDelayed(poll, POLL_MS);
        UUID target = room;
        if (target == null || loading) {
            return;
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            List<Message> history = app.messaging().history(app.auth().requireSession(), target);
            return history.isEmpty() ? null : history.get(history.size() - 1).id();
        }, latest -> {
            if (latest != null && !latest.equals(lastMessageId)) {
                load();
            }
        }, code -> {
        });
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        if (adapter.getItemCount() == 0) {
            States.loading(binding.state);
        }
        UUID knownRoom = room;
        UUID listingId = listingArg;
        UUID knownOther = otherId;
        String knownName = otherName;
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.auth().requireSession();
            UUID me = session.userId();
            UUID target = knownRoom;
            if (target == null) {
                WalkListing listing = app.walkListings().find(session, listingId)
                        .orElseThrow(() -> new ValidationException("walk.listing_closed"));
                target = app.messaging().openForWalk(session, listing, knownOther);
            }
            List<Message> history = app.messaging().history(session, target);
            try {
                app.messaging().markRead(session, target);
            } catch (AppException ignored) {
                // Unread markers are cosmetic; the chat still opens.
            }
            UUID other = knownOther;
            for (int i = history.size() - 1; other == null && i >= 0; i--) {
                if (!history.get(i).sentBy(me)) {
                    other = history.get(i).senderId();
                }
            }
            Set<UUID> ids = new LinkedHashSet<>();
            if (listingId != null) {
                ids.add(listingId);
            }
            for (Message message : history) {
                message.walkListingId().ifPresent(ids::add);
            }
            Map<UUID, WalkListing> found = new HashMap<>();
            Map<String, WalkOffer> foundOffers = new HashMap<>();
            for (UUID id : ids) {
                WalkListing listing = app.walkListings().find(session, id).orElse(null);
                if (listing == null) {
                    continue;
                }
                found.put(id, listing);
                if (listing.ownedBy(me)) {
                    if (listing.status() == WalkListingStatus.OPEN) {
                        for (WalkOffer offer : app.walkListings().offersFor(session, listing)) {
                            foundOffers.put(key(id, offer.walkerId()), offer);
                        }
                    }
                } else {
                    app.walkListings().myOfferFor(session, listing).ifPresent(offer -> foundOffers.put(key(id, me), offer));
                }
            }
            String name = knownName;
            if ((name == null || name.isBlank()) && other != null) {
                try {
                    name = app.discovery().publisher(session, other).map(PublicProfile::fullName).orElse("");
                } catch (AppException ignored) {
                    name = "";
                }
            }
            return new Loaded(target, me, other, name, history, found, foundOffers);
        }, this::show, code -> {
            loading = false;
            if (adapter.getItemCount() == 0) {
                States.message(binding.state, Codes.text(code), room == null ? null : this::load);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void show(Loaded loaded) {
        loading = false;
        room = loaded.room;
        me = loaded.me;
        otherId = loaded.other;
        otherName = loaded.otherName;
        listings = loaded.listings;
        offers = loaded.offers;
        boolean grew = loaded.history.size() != adapter.getItemCount();
        lastMessageId = loaded.history.isEmpty() ? null : loaded.history.get(loaded.history.size() - 1).id();
        adapter.replace(loaded.history, loaded.me, listings, offers);
        if (grew && !loaded.history.isEmpty()) {
            binding.messageList.scrollToPosition(loaded.history.size() - 1);
        }
        States.hide(binding.state);
        renderTitle();
        contextListing = listingArg;
        for (int i = loaded.history.size() - 1; contextListing == null && i >= 0; i--) {
            contextListing = loaded.history.get(i).walkListingId().orElse(null);
        }
        renderBanner(loaded.me);
    }

    private void renderTitle() {
        binding.toolbar.setTitle(otherName == null || otherName.isBlank() ? getString(R.string.wl_unknown_user) : otherName);
    }

    private void renderBanner(UUID me) {
        WalkListing listing = contextListing == null ? null : listings.get(contextListing);
        binding.listingBanner.setVisibility(listing == null ? View.GONE : View.VISIBLE);
        binding.priceButton.setVisibility(View.GONE);
        if (listing == null) {
            return;
        }
        Context context = requireContext();
        WalkListingDetails details = listing.details();
        binding.bannerTitle.setText(Labels.speciesEmoji(details.species()) + "  " + details.petName() + "  ·  "
                + getString(R.string.wl_per_hour, Labels.money(listing.effectivePricePerHour())));
        binding.bannerSubtitle.setText(Labels.date(details.startsAt()) + "  ·  " + Labels.duration(context, details.durationMinutes())
                + "  ·  " + Labels.status(context, listing.status()));
        WalkOffer mine = offers.get(key(listing.id(), me));
        boolean canPropose = !listing.ownedBy(me) && listing.acceptsOffers(Instant.now())
                && (mine == null || mine.status() != WalkOffer.Status.ACCEPTED);
        binding.priceButton.setVisibility(canPropose ? View.VISIBLE : View.GONE);
    }

    private void openProfile() {
        if (otherId != null) {
            Bundle args = new Bundle();
            args.putString("userId", otherId.toString());
            Ui.nav(this).navigate(R.id.action_global_public_profile, args);
        }
    }

    private void send() {
        String text = Ui.text(binding.messageInput).trim();
        UUID target = room;
        if (text.isEmpty() || target == null) {
            return;
        }
        UUID about = contextListing;
        binding.sendButton.setEnabled(false);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.messaging().send(app.auth().requireSession(), target, text, about);
        }, sent -> {
            binding.sendButton.setEnabled(true);
            binding.messageInput.setText("");
            load();
        }, code -> {
            binding.sendButton.setEnabled(true);
            Ui.snack(binding.getRoot(), code);
        });
    }

    private void proposePrice() {
        WalkListing listing = contextListing == null ? null : listings.get(contextListing);
        if (listing == null) {
            return;
        }
        Context context = requireContext();
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(getString(R.string.chat_price_title));
        layout.setHelperText(getString(R.string.wl_price_negotiable_hint, Labels.money(listing.details().pricePerHour())));
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        WalkOffer mine = me == null ? null : offers.get(key(listing.id(), me));
        BigDecimal current = mine == null ? listing.details().pricePerHour() : mine.priceFor(listing.details());
        input.setText(Labels.money(current));
        layout.addView(input);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_xl);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
        frame.setPadding(padding, padding / 2, padding, 0);
        frame.addView(layout);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.chat_price_button)
                .setView(frame)
                .setPositiveButton(R.string.chat_send, (dialog, which) -> sendPrice(listing, Ui.text(input)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void sendPrice(WalkListing listing, String text) {
        BigDecimal price;
        try {
            price = new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            Ui.snack(binding.getRoot(), R.string.validation_walk_price);
            return;
        }
        binding.priceButton.setEnabled(false);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.walkListings().offer(app.auth().requireSession(), listing, price, "");
        }, offerId -> {
            binding.priceButton.setEnabled(true);
            load();
        }, code -> {
            binding.priceButton.setEnabled(true);
            Ui.snack(binding.getRoot(), code);
        });
    }

    private void accept(Message message) {
        UUID listingId = message.walkListingId().orElse(null);
        WalkListing listing = listingId == null ? null : listings.get(listingId);
        WalkOffer offer = listingId == null ? null : offers.get(key(listingId, message.senderId()));
        BigDecimal price = message.pricePerHour().orElse(null);
        if (listing == null || offer == null || price == null) {
            return;
        }
        String name = otherName == null || otherName.isBlank() ? getString(R.string.wl_unknown_user) : otherName;
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.chat_accept_confirm, name, Labels.money(price)))
                .setPositiveButton(R.string.wl_accept, (dialog, which) -> Ui.run(this, () -> {
                    MiyavApplication app = Ui.app(this);
                    app.walkListings().respond(app.auth().requireSession(), listing, offer, true);
                    return Boolean.TRUE;
                }, done -> {
                    Ui.snackText(binding.getRoot(), getString(R.string.chat_accepted, Labels.money(price)));
                    load();
                }, code -> Ui.snack(binding.getRoot(), code)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static final class Loaded {
        final UUID room;
        final UUID me;
        final UUID other;
        final String otherName;
        final List<Message> history;
        final Map<UUID, WalkListing> listings;
        final Map<String, WalkOffer> offers;

        Loaded(UUID room, UUID me, UUID other, String otherName, List<Message> history,
               Map<UUID, WalkListing> listings, Map<String, WalkOffer> offers) {
            this.room = room;
            this.me = me;
            this.other = other;
            this.otherName = otherName;
            this.history = history;
            this.listings = listings;
            this.offers = offers;
        }
    }

    private static final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
        private final List<Message> items = new ArrayList<>();
        private final List<Boolean> acceptable = new ArrayList<>();
        private final java.util.function.Consumer<Message> onAccept;
        private Map<UUID, WalkListing> listings = Map.of();
        private UUID me;

        MessageAdapter(java.util.function.Consumer<Message> onAccept) {
            this.onAccept = onAccept;
        }

        /** The owner may accept only the walker's latest price, and only while that offer is pending at that price. */
        void replace(List<Message> next, UUID me, Map<UUID, WalkListing> listings, Map<String, WalkOffer> offers) {
            this.me = me;
            this.listings = listings;
            items.clear();
            items.addAll(next);
            Map<String, Integer> latest = new HashMap<>();
            for (int i = 0; i < next.size(); i++) {
                Message message = next.get(i);
                if (message.priceOffer() && message.walkListingId().isPresent()) {
                    latest.put(key(message.walkListingId().get(), message.senderId()), i);
                }
            }
            acceptable.clear();
            for (int i = 0; i < next.size(); i++) {
                Message message = next.get(i);
                boolean ok = false;
                if (message.priceOffer() && message.walkListingId().isPresent() && !message.sentBy(me)) {
                    String offerKey = key(message.walkListingId().get(), message.senderId());
                    WalkListing listing = listings.get(message.walkListingId().get());
                    WalkOffer offer = offers.get(offerKey);
                    ok = listing != null && listing.ownedBy(me) && listing.status() == WalkListingStatus.OPEN
                            && Integer.valueOf(i).equals(latest.get(offerKey))
                            && offer != null && offer.pending()
                            && offer.priceFor(listing.details()).compareTo(message.pricePerHour().get()) == 0;
                }
                acceptable.add(ok);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Message message = items.get(position);
            Context context = holder.itemView.getContext();
            boolean mine = message.sentBy(me);
            holder.binding.row.setGravity(mine ? Gravity.END : Gravity.START);
            int fill = message.priceOffer() ? R.color.pastel_butter : mine ? R.color.pastel_mint : R.color.surface;
            holder.binding.bubble.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, fill)));
            boolean price = message.priceOffer();
            holder.binding.priceLabel.setVisibility(price ? View.VISIBLE : View.GONE);
            holder.binding.priceView.setVisibility(price ? View.VISIBLE : View.GONE);
            holder.binding.priceTotal.setVisibility(View.GONE);
            if (price) {
                BigDecimal perHour = message.pricePerHour().get();
                holder.binding.priceView.setText("💰 " + context.getString(R.string.wl_per_hour, Labels.money(perHour)));
                WalkListing listing = message.walkListingId().map(listings::get).orElse(null);
                if (listing != null) {
                    int minutes = listing.details().durationMinutes();
                    holder.binding.priceTotal.setText(context.getString(R.string.wl_total,
                            Labels.money(WalkListingDetails.total(perHour, minutes)), Labels.duration(context, minutes)));
                    holder.binding.priceTotal.setVisibility(View.VISIBLE);
                }
            }
            String body = message.body();
            if (body.isEmpty() && message.image()) {
                body = context.getString(R.string.chat_photo);
            }
            holder.binding.bodyView.setText(body);
            holder.binding.bodyView.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
            holder.binding.acceptButton.setVisibility(acceptable.get(position) ? View.VISIBLE : View.GONE);
            holder.binding.acceptButton.setOnClickListener(view -> onAccept.accept(message));
            long millis = message.createdAt().toEpochMilli();
            int flags = DateUtils.FORMAT_SHOW_TIME | (DateUtils.isToday(millis) ? 0 : DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
            holder.binding.timeView.setText(DateUtils.formatDateTime(context, millis, flags));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemMessageBinding binding;

            Holder(ItemMessageBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
