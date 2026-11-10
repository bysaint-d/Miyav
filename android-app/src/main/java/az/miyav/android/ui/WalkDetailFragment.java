package az.miyav.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.DialogRateWalkerBinding;
import az.miyav.android.databinding.FragmentWalkDetailBinding;
import az.miyav.android.databinding.ItemWalkOfferBinding;
import az.miyav.common.error.AppException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.walk.MyWalks;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkListingStatus;
import az.miyav.domain.walk.WalkOffer;
import az.miyav.domain.walk.WalkerRating;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One walk request. Walkers send an offer; the owner sees the offers, chooses one walker and can cancel;
 * after the start time the owner or the chosen walker marks the walk as done.
 */
public class WalkDetailFragment extends Fragment {
    private FragmentWalkDetailBinding binding;
    private UUID listingId;
    private WalkListing listing;
    private WalkOffer myOffer;
    private boolean lastSignedIn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWalkDetailBinding.inflate(inflater, container, false);
        listingId = UUID.fromString(requireArguments().getString("listingId"));
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        binding.offerButton.setOnClickListener(view -> offer());
        binding.offerPriceInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                binding.offerPriceLayout.setError(null);
            }
        });
        binding.withdrawButton.setOnClickListener(view -> withdraw());
        binding.chatButton.setOnClickListener(view -> chat(null));
        binding.editButton.setOnClickListener(view -> {
            Bundle args = new Bundle();
            args.putString("listingId", listingId.toString());
            Ui.nav(this).navigate(R.id.action_global_walk_editor, args);
        });
        binding.cancelButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.wl_cancel_confirm)
                .setPositiveButton(R.string.wl_cancel, (dialog, which) -> act(
                        (app, session) -> app.walkListings().cancel(session, listing), R.string.wl_cancelled))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        binding.completeButton.setOnClickListener(view -> act(
                (app, session) -> app.walkListings().complete(session, listing), R.string.wl_completed));
        binding.rateButton.setOnClickListener(view -> rate());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        lastSignedIn = Ui.app(this).auth().signedIn();
        Ui.app(this).authState().observe(getViewLifecycleOwner(), state -> {
            boolean signedIn = Ui.app(this).auth().signedIn();
            if (signedIn != lastSignedIn) {
                lastSignedIn = signedIn;
                load();
            }
        });
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
        if (listing == null) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession viewer = app.auth().viewer();
            WalkListing found = app.walkListings().find(viewer, listingId).orElse(null);
            if (found == null) {
                return new Loaded(null, null, null, null, List.of(), null, null, Map.of());
            }
            boolean owns = viewer != null && found.ownedBy(viewer.userId());
            var owner = Ui.async(() -> profile(app, viewer, found.ownerId()));
            var walker = Ui.async(() -> found.walkerId().map(id -> profile(app, viewer, id)).orElse(null));
            var offered = Ui.async(() -> owns ? app.walkListings().offersFor(viewer, found) : List.<WalkOffer>of());
            var mine = Ui.async(() -> viewer != null && !owns ? app.walkListings().myOfferFor(viewer, found).orElse(null) : null);
            PendingAction resume = viewer != null && !owns
                    ? app.auth().takePending(ProtectedAction.OFFER_WALK, "listingId", listingId.toString()).orElse(null) : null;
            List<WalkOffer> received = Ui.join(offered);
            List<UUID> walkers = new ArrayList<>();
            found.walkerId().ifPresent(walkers::add);
            received.forEach(offer -> walkers.add(offer.walkerId()));
            var ratings = Ui.async(() -> ratings(app, viewer, walkers));
            List<java.util.concurrent.CompletableFuture<PublicProfile>> people = new ArrayList<>();
            for (WalkOffer offer : received) {
                people.add(Ui.async(() -> profile(app, viewer, offer.walkerId())));
            }
            List<OfferRow> offers = new ArrayList<>();
            for (int i = 0; i < received.size(); i++) {
                offers.add(new OfferRow(received.get(i), Ui.join(people.get(i))));
            }
            return new Loaded(found, viewer == null ? null : viewer.userId(), Ui.join(owner), Ui.join(walker), offers,
                    Ui.join(mine), resume, Ui.join(ratings));
        }, this::show, code -> {
            if (listing == null) {
                States.message(binding.state, Codes.text(code), this::load);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    /** Ratings are a nice-to-have on this screen; without them the walk still shows. */
    private static Map<UUID, WalkerRating> ratings(MiyavApplication app, AuthSession viewer, List<UUID> walkers) {
        if (viewer == null || walkers.isEmpty()) {
            return Map.of();
        }
        try {
            return app.walkListings().walkerRatings(viewer, walkers);
        } catch (AppException exception) {
            return Map.of();
        }
    }

    private static PublicProfile profile(MiyavApplication app, AuthSession viewer, UUID userId) {
        try {
            return app.discovery().publisher(viewer, userId).orElse(null);
        } catch (AppException exception) {
            return null;
        }
    }

    private void show(Loaded loaded) {
        if (loaded.listing == null) {
            binding.content.setVisibility(View.GONE);
            States.message(binding.state, R.string.walk_listing_closed, null);
            return;
        }
        listing = loaded.listing;
        myOffer = loaded.mine;
        Context context = requireContext();
        WalkListingDetails details = listing.details();
        States.hide(binding.state);
        binding.content.setVisibility(View.VISIBLE);
        binding.speciesBadge.setText(Labels.speciesEmoji(details.species()));
        binding.speciesBadge.setBackgroundTintList(ColorStateList.valueOf(Labels.speciesColor(context, details.species())));
        String photo = listing.petPhotoPath().orElse(null);
        binding.petPhotoView.setVisibility(photo == null ? View.GONE : View.VISIBLE);
        if (photo != null) {
            Ui.app(this).photos().walkPet(binding.petPhotoView, photo);
            binding.petPhotoView.setOnClickListener(view -> PhotoViewerDialog.openWalkPet(getChildFragmentManager(), photo));
        }
        binding.titleView.setText(details.petName());
        binding.statusView.setText(Labels.status(context, listing.status()));
        boolean agreed = listing.agreedPricePerHour().isPresent();
        binding.priceView.setText(agreed
                ? getString(R.string.wl_agreed, Labels.money(listing.effectivePricePerHour()))
                : getString(R.string.wl_per_hour, Labels.money(details.pricePerHour())));
        binding.totalView.setText(getString(R.string.wl_total, Labels.money(listing.effectiveTotal()),
                Labels.duration(context, details.durationMinutes())));
        boolean differs = agreed && listing.effectivePricePerHour().compareTo(details.pricePerHour()) != 0;
        binding.askedView.setVisibility(differs ? View.VISIBLE : View.GONE);
        binding.askedView.setText(getString(R.string.wl_asked, Labels.money(details.pricePerHour())));
        binding.timeView.setText("📅  " + Labels.date(details.startsAt()) + "  ·  ⏱  " + Labels.duration(context, details.durationMinutes()));
        binding.placeView.setText(Labels.walkPlace(details));
        binding.notesView.setText(details.notes());
        binding.notesView.setVisibility(details.notes().isEmpty() ? View.GONE : View.VISIBLE);
        person(binding.ownerView, loaded.owner, listing.ownerId());
        binding.walkerSection.setVisibility(listing.walkerId().isPresent() ? View.VISIBLE : View.GONE);
        listing.walkerId().ifPresent(id -> person(binding.walkerView, loaded.walker, id));

        Instant now = Instant.now();
        boolean owner = loaded.viewer != null && listing.ownedBy(loaded.viewer);
        boolean walker = loaded.viewer != null && listing.walkedBy(loaded.viewer);
        showRating(loaded, owner || walker);
        boolean canOffer = !owner && listing.acceptsOffers(now)
                && (myOffer == null || myOffer.status() != WalkOffer.Status.ACCEPTED);
        binding.offerSection.setVisibility(canOffer ? View.VISIBLE : View.GONE);
        if (canOffer) {
            binding.offerPriceLayout.setHelperText(getString(R.string.wl_price_negotiable_hint, Labels.money(details.pricePerHour())));
            if (Ui.text(binding.offerPriceInput).isEmpty()) {
                binding.offerPriceInput.setText(Labels.money(myOffer == null ? details.pricePerHour() : myOffer.priceFor(details)));
            }
            binding.offerButton.setText(myOffer == null ? R.string.wl_offer : R.string.wl_offer_update);
        }
        showMyOffer();
        boolean interested = myOffer != null || walker || listing.acceptsOffers(now);
        binding.chatButton.setVisibility((!owner && interested) || (owner && listing.walkerId().isPresent())
                ? View.VISIBLE : View.GONE);
        binding.offersSection.setVisibility(owner && listing.status() != WalkListingStatus.CANCELLED ? View.VISIBLE : View.GONE);
        if (owner) {
            showOffers(loaded.offers, loaded.ratings);
        }
        binding.editButton.setVisibility(owner && listing.status() == WalkListingStatus.OPEN ? View.VISIBLE : View.GONE);
        binding.cancelButton.setVisibility(owner && listing.status().active() ? View.VISIBLE : View.GONE);
        binding.completeButton.setVisibility((owner || walker) && listing.completable(now) ? View.VISIBLE : View.GONE);
        renderStage(loaded, owner, walker, now);
        binding.messageView.setText("");
        if (loaded.resume != null && binding.offerSection.getVisibility() == View.VISIBLE) {
            String draft = loaded.resume.argument("draft");
            if (Ui.text(binding.offerInput).isEmpty() && !draft.isEmpty()) {
                binding.offerInput.setText(draft);
            }
            binding.content.post(() -> {
                if (binding != null) {
                    binding.content.smoothScrollTo(0, binding.offerSection.getTop());
                    binding.offerInput.requestFocus();
                }
            });
        }
    }

    /** The card at the top that says where this walk stands for the viewer and what happens next. */
    private void renderStage(Loaded loaded, boolean owner, boolean walker, Instant now) {
        if (loaded.viewer == null || (!owner && !walker && myOffer == null)) {
            binding.stageCard.setVisibility(View.GONE);
            return;
        }
        Context context = requireContext();
        int pending = (int) loaded.offers.stream().filter(row -> row.offer.pending()).count();
        MyWalks view = new MyWalks(loaded.viewer, List.of(listing),
                myOffer == null ? Map.of() : Map.of(listing.id(), myOffer),
                owner ? Map.of(listing.id(), pending) : Map.of());
        MyWalks.Stage stage = view.stage(listing, now);
        WalkListingDetails details = listing.details();
        String price = Labels.money(listing.effectivePricePerHour());
        String when = Labels.date(details.startsAt());
        String title;
        String body;
        String hint = null;
        switch (stage) {
            case WAITING_FOR_OFFERS -> {
                title = getString(R.string.wl_banner_waiting_title);
                body = getString(R.string.wl_banner_waiting_body);
            }
            case OFFERS_TO_REVIEW -> {
                title = getResources().getQuantityString(R.plurals.wl_banner_review_title, pending, pending);
                body = getString(R.string.wl_banner_review_body);
            }
            case OFFER_SENT -> {
                title = getString(R.string.wl_banner_sent_title);
                body = getString(R.string.wl_banner_sent_body);
            }
            case AGREED -> {
                title = getString(R.string.wl_banner_agreed_title);
                String walkerName = loaded.walker == null || loaded.walker.fullName().isBlank()
                        ? getString(R.string.wl_unknown_user) : loaded.walker.fullName();
                body = owner ? getString(R.string.wl_banner_agreed_owner, walkerName, details.petName(), price, when)
                        : getString(R.string.wl_banner_agreed_walker, details.petName(), price, when);
                hint = getString(R.string.wl_banner_agreed_hint);
            }
            case OFFER_DECLINED -> {
                title = getString(R.string.wl_banner_declined_title);
                body = getString(R.string.wl_banner_declined_body);
            }
            case OFFER_WITHDRAWN -> {
                title = getString(R.string.wl_banner_withdrawn_title);
                body = getString(R.string.wl_banner_withdrawn_body);
            }
            case COMPLETED -> {
                title = getString(R.string.wl_banner_completed_title);
                body = getString(R.string.wl_banner_completed_body);
            }
            case EXPIRED -> {
                title = getString(R.string.wl_banner_expired_title);
                body = getString(R.string.wl_banner_expired_body);
            }
            default -> {
                title = getString(R.string.wl_banner_cancelled_title);
                body = getString(R.string.wl_banner_cancelled_body);
            }
        }
        binding.stageCard.setVisibility(View.VISIBLE);
        binding.stageCard.setBackgroundTintList(ColorStateList.valueOf(WalkStages.card(context, stage)));
        binding.stageIcon.setText(WalkStages.icon(stage));
        binding.stageTitle.setText(title);
        binding.stageBody.setText(body);
        binding.stageHint.setVisibility(hint == null ? View.GONE : View.VISIBLE);
        binding.stageHint.setText(hint);
        binding.stageCard.setOnClickListener(stage == MyWalks.Stage.AGREED ? card -> chat(null)
                : stage == MyWalks.Stage.OFFERS_TO_REVIEW ? card -> binding.content.smoothScrollTo(0, binding.offersSection.getTop())
                : null);
        binding.stageCard.setClickable(stage == MyWalks.Stage.AGREED || stage == MyWalks.Stage.OFFERS_TO_REVIEW);
    }

    private void person(android.widget.TextView view, PublicProfile profile, UUID userId) {
        view.setText(profile == null || profile.fullName().isBlank() ? getString(R.string.wl_unknown_user) : profile.fullName());
        view.setOnClickListener(profile == null ? null : v -> openProfile(userId));
    }

    private void openProfile(UUID userId) {
        Bundle args = new Bundle();
        args.putString("userId", userId.toString());
        Ui.nav(this).navigate(R.id.action_global_public_profile, args);
    }

    private void showMyOffer() {
        if (myOffer == null) {
            binding.myOfferSection.setVisibility(View.GONE);
            return;
        }
        binding.myOfferSection.setVisibility(View.VISIBLE);
        String status = getString(switch (myOffer.status()) {
            case PENDING -> R.string.wl_offer_pending;
            case ACCEPTED -> R.string.wl_you_walk;
            case DECLINED -> R.string.wl_offer_declined;
            case WITHDRAWN -> R.string.walk_offer_closed;
        });
        binding.myOfferView.setText(getString(R.string.wl_offer_your_price, Labels.money(myOffer.priceFor(listing.details())))
                + "\n" + status);
        binding.withdrawButton.setVisibility(myOffer.pending() ? View.VISIBLE : View.GONE);
    }

    /** The walker's average under their name and, for the two people involved, this walk's stars and comment. */
    private void showRating(Loaded loaded, boolean involved) {
        WalkerRating average = listing.walkerId().map(loaded.ratings::get).orElse(null);
        binding.walkerRatingView.setVisibility(average == null ? View.GONE : View.VISIBLE);
        if (average != null) {
            binding.walkerRatingView.setText(averageLine(average));
        }
        Integer stars = listing.walkerRating().orElse(null);
        binding.reviewView.setVisibility(involved && stars != null ? View.VISIBLE : View.GONE);
        if (involved && stars != null) {
            String review = listing.walkerReview().isBlank() ? "" : "\n“" + listing.walkerReview() + "”";
            binding.reviewView.setText(getString(R.string.wl_rated_line, Labels.stars(stars)) + review);
        }
        binding.rateButton.setVisibility(loaded.viewer != null && listing.rateableBy(loaded.viewer) ? View.VISIBLE : View.GONE);
    }

    private String averageLine(WalkerRating rating) {
        return getString(R.string.wl_rating_average, Labels.rating(rating.average()),
                getResources().getQuantityString(R.plurals.profile_rating_count, rating.count(), rating.count()));
    }

    private void rate() {
        if (listing == null) {
            return;
        }
        DialogRateWalkerBinding dialog = DialogRateWalkerBinding.inflate(getLayoutInflater());
        WalkListing target = listing;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wl_rate_walker)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.wl_rate_send, (d, which) -> {
                    int stars = Math.round(dialog.ratingBar.getRating());
                    String review = Ui.text(dialog.reviewInput).trim();
                    act((app, session) -> app.walkListings().rate(session, target, stars, review), R.string.wl_rated);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showOffers(List<OfferRow> offers, Map<UUID, WalkerRating> ratings) {
        long active = offers.stream().filter(row -> row.offer.status() != WalkOffer.Status.WITHDRAWN).count();
        binding.offersTitle.setText(getString(R.string.wl_offers_title, (int) active));
        binding.offersEmpty.setVisibility(active == 0 ? View.VISIBLE : View.GONE);
        binding.offersList.removeAllViews();
        boolean open = listing.status() == WalkListingStatus.OPEN;
        for (OfferRow row : offers) {
            if (row.offer.status() == WalkOffer.Status.WITHDRAWN) {
                continue;
            }
            ItemWalkOfferBinding item = ItemWalkOfferBinding.inflate(getLayoutInflater(), binding.offersList, false);
            String name = row.profile == null || row.profile.fullName().isBlank()
                    ? getString(R.string.wl_unknown_user) : row.profile.fullName();
            item.nameView.setText(name);
            if (row.profile != null && row.profile.verified()) {
                item.nameView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_verified, 0);
            }
            Ui.app(this).photos().avatar(item.avatarView, row.profile == null ? null : row.profile.avatarPath());
            item.walkerRow.setOnClickListener(view -> openProfile(row.offer.walkerId()));
            String status = row.offer.pending() ? "" : "  ·  " + offerStatus(row.offer.status());
            String date = row.offer.createdAt() == null ? status.trim() : Labels.date(row.offer.createdAt()) + status;
            WalkerRating rating = ratings.get(row.offer.walkerId());
            item.dateView.setText(rating == null ? date : averageLine(rating) + (date.isEmpty() ? "" : "  ·  " + date));
            item.messageView.setText(row.offer.message());
            item.messageView.setVisibility(row.offer.message().isEmpty() ? View.GONE : View.VISIBLE);
            item.priceView.setText(getString(R.string.wl_per_hour, Labels.money(row.offer.priceFor(listing.details()))));
            item.counterBadge.setVisibility(row.offer.counterOffer(listing.details()) ? View.VISIBLE : View.GONE);
            item.messageButton.setOnClickListener(view -> chat(row.offer.walkerId()));
            item.actions.setVisibility(open && row.offer.pending() ? View.VISIBLE : View.GONE);
            item.acceptButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.wl_accept_confirm, name))
                    .setPositiveButton(R.string.wl_accept, (dialog, which) -> respond(row.offer, true))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
            item.declineButton.setOnClickListener(view -> respond(row.offer, false));
            binding.offersList.addView(item.getRoot());
        }
    }

    private String offerStatus(WalkOffer.Status status) {
        return getString(switch (status) {
            case PENDING -> R.string.adoption_status_pending;
            case ACCEPTED -> R.string.adoption_status_accepted;
            case DECLINED -> R.string.adoption_status_rejected;
            case WITHDRAWN -> R.string.adoption_status_cancelled;
        });
    }

    private void offer() {
        if (listing == null) {
            return;
        }
        String message = Ui.text(binding.offerInput).trim();
        PendingAction intent = PendingAction.of(ProtectedAction.OFFER_WALK, "listingId", listingId.toString());
        if (!Gate.require(this, message.isEmpty() ? intent : intent.with("draft", message))) {
            return;
        }
        BigDecimal price;
        try {
            price = WalkListingDetails.requireValidPrice(new BigDecimal(Ui.text(binding.offerPriceInput).trim().replace(',', '.')));
        } catch (NumberFormatException | ValidationException exception) {
            binding.offerPriceLayout.setError(getString(R.string.validation_walk_price));
            return;
        }
        WalkListing target = listing;
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.walkListings().offer(app.auth().requireSession(), target, price, message);
        }, sent -> {
            busy(false);
            binding.offerInput.setText("");
            Ui.snack(binding.getRoot(), R.string.wl_offer_sent);
            load();
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    /** The walker writes to the owner; the owner writes to {@code walkerId} (or the chosen walker). */
    private void chat(UUID walkerId) {
        if (listing == null || !Gate.require(this, ProtectedAction.SEND_MESSAGE)) {
            return;
        }
        UUID with = walkerId != null ? walkerId : listing.walkerId().orElse(null);
        Ui.openChat(this, null, listingId.toString(), with == null ? null : with.toString(), null);
    }

    private void withdraw() {
        if (myOffer == null) {
            return;
        }
        WalkOffer target = myOffer;
        act((app, session) -> app.walkListings().withdraw(session, target), 0);
    }

    private void respond(WalkOffer offer, boolean accept) {
        WalkListing target = listing;
        act((app, session) -> app.walkListings().respond(session, target, offer, accept), 0);
    }

    /** Runs one signed-in change and reloads the screen; {@code done} is an optional confirmation. */
    private void act(Action action, int done) {
        if (listing == null) {
            return;
        }
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            action.run(app, app.auth().requireSession());
            return Boolean.TRUE;
        }, ok -> {
            busy(false);
            if (done != 0) {
                Ui.snack(binding.getRoot(), done);
            }
            load();
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
            load();
        });
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.offerButton.setEnabled(!busy);
        binding.withdrawButton.setEnabled(!busy);
        binding.cancelButton.setEnabled(!busy);
        binding.completeButton.setEnabled(!busy);
        binding.rateButton.setEnabled(!busy);
        binding.editButton.setEnabled(!busy);
        for (int i = 0; i < binding.offersList.getChildCount(); i++) {
            setEnabledDeep(binding.offersList.getChildAt(i), !busy);
        }
    }

    private static void setEnabledDeep(View view, boolean enabled) {
        if (view instanceof com.google.android.material.button.MaterialButton) {
            view.setEnabled(enabled);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabledDeep(group.getChildAt(i), enabled);
            }
        }
    }

    private interface Action {
        void run(MiyavApplication app, AuthSession session);
    }

    private static final class OfferRow {
        final WalkOffer offer;
        final PublicProfile profile;

        OfferRow(WalkOffer offer, PublicProfile profile) {
            this.offer = offer;
            this.profile = profile;
        }
    }

    private static final class Loaded {
        final WalkListing listing;
        final UUID viewer;
        final PublicProfile owner;
        final PublicProfile walker;
        final List<OfferRow> offers;
        final WalkOffer mine;
        final PendingAction resume;
        final Map<UUID, WalkerRating> ratings;

        Loaded(WalkListing listing, UUID viewer, PublicProfile owner, PublicProfile walker, List<OfferRow> offers,
               WalkOffer mine, PendingAction resume, Map<UUID, WalkerRating> ratings) {
            this.listing = listing;
            this.viewer = viewer;
            this.owner = owner;
            this.walker = walker;
            this.offers = offers;
            this.mine = mine;
            this.resume = resume;
            this.ratings = ratings;
        }
    }
}
