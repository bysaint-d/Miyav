package az.miyav.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polygon;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.data.SyncOutboxEntry;
import az.miyav.android.databinding.FragmentPetDetailBinding;
import az.miyav.android.databinding.ViewPublisherCardBinding;
import az.miyav.android.sync.OutboxPolicy;
import az.miyav.android.sync.OutboxSyncWorker;
import az.miyav.common.error.AppException;
import az.miyav.common.error.PersistenceException;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.pet.Vaccination;
import az.miyav.domain.premium.PremiumRequest;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.user.User;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PetDetailFragment extends Fragment {
    /** Coming back from a profile or a photo does not reload a listing that was read this recently. */
    private static final long FRESH_MS = 20_000;
    private FragmentPetDetailBinding binding;
    private PhotoAdapter photos;
    private MiniListingAdapter others;
    private UUID petId;
    private Pet pet;
    private UUID viewer;
    private boolean lastSignedIn;
    private long loadedAt;
    private boolean stale;
    private Loaded last;
    private Publisher publisher;
    private UUID publisherLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPetDetailBinding.inflate(inflater, container, false);
        petId = UUID.fromString(requireArguments().getString("petId"));
        binding.backButton.setOnClickListener(view -> Ui.nav(this).popBackStack());
        photos = new PhotoAdapter(Ui.app(this), null);
        photos.onOpen(position -> {
            if (pet != null) {
                PhotoViewerDialog.open(getChildFragmentManager(), pet.photoPaths(), position);
            }
        });
        LinearLayoutManager layout = new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false);
        binding.photoList.setLayoutManager(layout);
        binding.photoList.setAdapter(photos);
        new PagerSnapHelper().attachToRecyclerView(binding.photoList);
        binding.photoList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && pet != null && pet.photoPaths().size() > 1) {
                    int position = Math.max(0, layout.findFirstCompletelyVisibleItemPosition());
                    binding.photoCounter.setText(getString(R.string.format_counter, position + 1, pet.photoPaths().size()));
                }
            }
        });
        binding.content.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                (view, x, y, oldX, oldY) -> renderTopBar(y));
        others = new MiniListingAdapter(Ui.app(this), other -> {
            Bundle args = new Bundle();
            args.putString("petId", other.id().toString());
            Ui.nav(this).navigate(R.id.action_global_detail, args);
        });
        binding.otherList.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        binding.otherList.setAdapter(others);
        MapSupport.style(binding.mapPreview, false);
        binding.applyButton.setOnClickListener(view -> apply());
        binding.editButton.setOnClickListener(view -> {
            stale = true;
            Bundle args = new Bundle();
            args.putString("petId", petId.toString());
            Ui.nav(this).navigate(R.id.action_detail_to_editor, args);
        });
        binding.premiumButton.setOnClickListener(view -> {
            if (pet != null) {
                PremiumSheet.open(this, pet);
            }
        });
        getChildFragmentManager().setFragmentResultListener(PremiumSheet.RESULT, getViewLifecycleOwner(), (key, result) -> {
            Ui.snack(binding.getRoot(), R.string.premium_sent);
            load();
        });
        binding.shareButton.setOnClickListener(view -> share());
        binding.favoriteButton.setOnClickListener(view -> toggleFavorite());
        View.OnClickListener openMap = view -> {
            if (pet != null && pet.location() != null) {
                Contact.map(requireContext(), pet.location().latitude(), pet.location().longitude(), pet.name());
            }
        };
        binding.openMapButton.setOnClickListener(openMap);
        binding.mapPreview.setOnClickListener(openMap);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (pet == null) {
            Pet cached = Ui.app(this).cache().listing(petId);
            if (cached != null) {
                az.miyav.domain.auth.AuthSession signedIn = Ui.app(this).auth().viewer();
                show(Loaded.partial(cached, signedIn == null ? null : signedIn.userId()));
            }
        } else if (last != null) {
            show(last);
        }
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
        binding.mapPreview.onResume();
        if (stale || loadedAt == 0 || android.os.SystemClock.elapsedRealtime() - loadedAt > FRESH_MS) {
            stale = false;
            load();
        }
    }

    @Override
    public void onPause() {
        binding.mapPreview.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        binding.mapPreview.onDetach();
        publisherLoading = null;
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (pet == null) {
            binding.skeleton.setVisibility(View.VISIBLE);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession viewer = app.auth().viewer();
            Pet found = app.discovery().listing(viewer, petId).orElse(null);
            AdoptionApplication mine = null;
            if (viewer != null && found != null && !found.ownerId().equals(viewer.userId())) {
                for (AdoptionApplication application : app.adoptionService().mine(viewer)) {
                    if (application.petId().equals(petId) && application.status() != ApplicationStatus.CANCELLED) {
                        mine = application;
                        break;
                    }
                }
            }
            boolean favorite = false;
            try {
                favorite = app.favorites().isFavorite(viewer, petId);
            } catch (AppException ignored) {
                // The heart starts empty when favourites cannot be read; the listing still shows.
            }
            PendingAction resume = null;
            if (viewer != null) {
                resume = app.auth().takePending(ProtectedAction.APPLY_ADOPTION, "petId", petId.toString()).orElse(null);
                if (resume == null) {
                    resume = app.auth().takePending(ProtectedAction.CONTACT_OWNER, "petId", petId.toString()).orElse(null);
                }
            }
            PremiumRequest premium = null;
            if (viewer != null && found != null && found.ownerId().equals(viewer.userId())) {
                try {
                    premium = app.premium().latest(viewer, petId).orElse(null);
                } catch (AppException ignored) {
                    // The premium button still works; only the last request's state is missing.
                }
            }
            return new Loaded(found, mine, viewer == null ? null : viewer.userId(), favorite, resume, premium);
        }, loaded -> {
            loadedAt = android.os.SystemClock.elapsedRealtime();
            if (loaded.pet != null) {
                Ui.app(this).cache().remember(loaded.pet);
            }
            show(loaded);
        }, code -> {
            binding.skeleton.setVisibility(View.GONE);
            if (pet == null) {
                States.message(binding.state, Codes.text(code), this::load);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void show(Loaded loaded) {
        binding.skeleton.setVisibility(View.GONE);
        if (loaded.pet == null) {
            binding.content.setVisibility(View.GONE);
            States.message(binding.state, R.string.listing_unavailable, null);
            return;
        }
        pet = loaded.pet;
        viewer = loaded.viewer;
        if (!loaded.partial) {
            last = loaded.kept(loaded.favorite);
        }
        Context context = requireContext();
        States.hide(binding.state);
        binding.content.setVisibility(View.VISIBLE);
        binding.titleView.setText(pet.name());
        binding.statusChip.setText(Labels.status(context, pet.status()));
        binding.locationLine.setText(Labels.place(pet));
        binding.factChips.removeAllViews();
        if (pet.premium(java.time.Instant.now())) {
            addFact("⭐  " + getString(R.string.premium_badge), color(R.color.brand_sun));
        }
        addFact(Labels.speciesEmoji(pet.species()) + "  " + Labels.species(context, pet.species()), Labels.speciesColor(context, pet.species()));
        if (!pet.breed().isEmpty()) {
            addFact(getString(R.string.format_label_value, getString(R.string.pet_breed), pet.breed()), color(R.color.pastel_sky));
        }
        if (pet.gender() != PetGender.UNKNOWN) {
            String symbol = pet.gender() == PetGender.MALE ? "♂  " : "♀  ";
            addFact(symbol + Labels.gender(context, pet.gender()), color(pet.gender() == PetGender.MALE ? R.color.pastel_sky : R.color.pastel_peach));
        }
        addFact("🎂  " + Labels.age(context, pet.ageMonths()), color(R.color.pastel_butter));
        if (pet.vaccination() != Vaccination.UNKNOWN) {
            addFact("💉  " + Labels.vaccination(context, pet.vaccination()),
                    color(pet.vaccination() == Vaccination.YES ? R.color.pastel_success : R.color.surface_muted));
        }
        binding.bodyView.setText(pet.description());
        binding.careView.setText(Labels.care(context, pet));
        photos.replace(pet.photoPaths());
        binding.photoCounter.setVisibility(pet.photoPaths().size() > 1 ? View.VISIBLE : View.GONE);
        binding.photoCounter.setText(getString(R.string.format_counter, 1, Math.max(1, pet.photoPaths().size())));
        showLocation(pet.location());
        boolean owner = pet.ownerId().equals(loaded.viewer);
        binding.shareButton.setVisibility(pet.status() == PetStatus.PUBLISHED ? View.VISIBLE : View.GONE);
        binding.favoriteButton.setVisibility(pet.status() == PetStatus.PUBLISHED && !owner ? View.VISIBLE : View.GONE);
        showFavorite(loaded.favorite);
        binding.ownerBadge.setVisibility(owner ? View.VISIBLE : View.GONE);
        binding.editButton.setVisibility(owner && pet.status() != PetStatus.ARCHIVED ? View.VISIBLE : View.GONE);
        showPremium(owner && !loaded.partial, loaded.premium);
        boolean canApply = !owner && pet.status() == PetStatus.PUBLISHED && loaded.mine == null && !loaded.partial;
        binding.applySection.setVisibility(canApply ? View.VISIBLE : View.GONE);
        if (loaded.partial) {
            binding.messageView.setText("");
        } else if (loaded.mine != null) {
            binding.messageView.setText(getString(R.string.format_label_value,
                    getString(R.string.nav_applications), Labels.status(context, loaded.mine.status())));
        } else if (!owner && pet.status() != PetStatus.PUBLISHED) {
            binding.messageView.setText(R.string.adoption_listing_unavailable);
        } else {
            binding.messageView.setText("");
        }
        loadPublisher(pet.ownerId(), owner);
        if (loaded.resume != null) {
            resume(loaded.resume, canApply);
        }
    }

    /** The owner's premium state and the button to buy or extend it. */
    private void showPremium(boolean owner, PremiumRequest request) {
        boolean eligible = owner && pet.status() == PetStatus.PUBLISHED;
        binding.premiumSection.setVisibility(eligible ? View.VISIBLE : View.GONE);
        if (!eligible) {
            return;
        }
        String price = Labels.money(az.miyav.domain.premium.PremiumPlan.PRICE);
        java.time.Instant until = pet.extras().premiumUntil().orElse(null);
        boolean active = pet.premium(java.time.Instant.now());
        boolean pending = request != null && request.pending();
        String status = null;
        if (pending) {
            status = getString(R.string.premium_pending);
        } else if (active) {
            status = getString(R.string.premium_active, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date.from(until)));
        } else if (request != null && request.status() == PremiumRequest.Status.REJECTED) {
            status = getString(R.string.premium_rejected, request.note()).trim();
        }
        binding.premiumStatus.setVisibility(status == null ? View.GONE : View.VISIBLE);
        binding.premiumStatus.setText(status);
        binding.premiumButton.setVisibility(pending ? View.GONE : View.VISIBLE);
        binding.premiumButton.setText(getString(active ? R.string.premium_extend : R.string.premium_cta, price));
    }

    /** Returns the user to what they started as a guest: the application form with their text, or the contacts. */
    private void resume(PendingAction action, boolean canApply) {
        if (action.action() == ProtectedAction.APPLY_ADOPTION && canApply) {
            String draft = action.argument("draft");
            if (Ui.text(binding.messageInput).isEmpty() && !draft.isEmpty()) {
                binding.messageInput.setText(draft);
            }
            binding.content.post(() -> {
                if (binding != null) {
                    binding.content.smoothScrollTo(0, binding.applySection.getTop() + binding.sheet.getTop());
                    binding.messageInput.requestFocus();
                }
            });
            Ui.snack(binding.getRoot(), R.string.guest_continue_application);
        } else if (action.action() == ProtectedAction.CONTACT_OWNER) {
            binding.content.post(() -> {
                if (binding != null) {
                    binding.content.smoothScrollTo(0, binding.publisher.getRoot().getTop() + binding.sheet.getTop());
                }
            });
        }
    }

    private void showFavorite(boolean favorite) {
        binding.favoriteButton.setTag(favorite);
        binding.favoriteButton.setImageResource(favorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        binding.favoriteButton.setContentDescription(getString(favorite ? R.string.favorites_remove : R.string.favorites_add));
    }

    /** Guests save on the device, signed-in users on their account. No sign-in is needed to save. */
    private void toggleFavorite() {
        boolean next = !Boolean.TRUE.equals(binding.favoriteButton.getTag());
        showFavorite(next);
        binding.favoriteButton.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120)
                .withEndAction(() -> binding.favoriteButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.favorites().set(app.auth().viewer(), petId, next);
        }, saved -> {
            if (last != null) {
                last = last.kept(next);
            }
        }, code -> {
            showFavorite(!next);
            Ui.snack(binding.getRoot(), code);
        });
    }

    private void showLocation(ListingLocation location) {
        binding.locationSection.setVisibility(location == null ? View.GONE : View.VISIBLE);
        if (location == null) {
            return;
        }
        GeoPoint point = new GeoPoint(location.latitude(), location.longitude());
        binding.mapPreview.getOverlays().clear();
        Polygon circle = new Polygon(binding.mapPreview);
        circle.setPoints(Polygon.pointsAsCircle(point, 150));
        circle.getFillPaint().setColor(0x33FF6B4A);
        circle.getOutlinePaint().setColor(ContextCompat.getColor(requireContext(), R.color.brand_coral));
        circle.getOutlinePaint().setStrokeWidth(4f);
        circle.setInfoWindow(null);
        binding.mapPreview.getOverlays().add(circle);
        binding.mapPreview.getController().setZoom(15.5);
        binding.mapPreview.getController().setCenter(point);
        binding.mapPreview.invalidate();
    }

    /** Loads the publisher card once per screen; it is redrawn from memory when the user comes back. */
    private void loadPublisher(UUID ownerId, boolean owner) {
        if (publisher == null || !publisher.ownerId.equals(ownerId)) {
            ScreenCache.Publisher cached = Ui.app(this).cache().publisher(ownerId);
            if (cached != null) {
                publisher = new Publisher(ownerId, cached.profile, without(cached.listings), false);
            }
        }
        if (publisher != null && publisher.ownerId.equals(ownerId)) {
            showPublisher(publisher, owner);
            if (publisher.fresh) {
                return;
            }
        } else {
            binding.publisherSkeleton.setVisibility(View.VISIBLE);
            binding.publisher.getRoot().setVisibility(View.GONE);
        }
        if (ownerId.equals(publisherLoading)) {
            return;
        }
        publisherLoading = ownerId;
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession viewer = app.auth().viewer();
            Optional<PublicProfile> profile = app.discovery().publisher(viewer, ownerId);
            List<Pet> all = profile.isPresent() ? app.discovery().byPublisher(viewer, ownerId) : List.of();
            app.cache().remember(ownerId, profile.orElse(null), all);
            return new Publisher(ownerId, profile.orElse(null), without(all), true);
        }, loaded -> {
            publisherLoading = null;
            publisher = loaded;
            showPublisher(loaded, pet != null && pet.ownerId().equals(viewer));
        }, code -> {
            publisherLoading = null;
            binding.publisherSkeleton.setVisibility(View.GONE);
            if (publisher == null) {
                bindPublisher(binding.publisher, null, owner);
            }
        });
    }

    private List<Pet> without(List<Pet> listings) {
        List<Pet> more = new ArrayList<>();
        for (Pet other : listings) {
            if (!other.id().equals(petId)) {
                more.add(other);
            }
        }
        return more;
    }

    private void showPublisher(Publisher loaded, boolean owner) {
        binding.publisherSkeleton.setVisibility(View.GONE);
        bindPublisher(binding.publisher, loaded.profile, owner);
        others.replace(loaded.others);
        binding.otherSection.setVisibility(loaded.others.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Once the photo has scrolled away, the floating buttons get a solid bar with the pet's name. */
    private void renderTopBar(int scrollY) {
        if (binding == null) {
            return;
        }
        int threshold = Math.max(1, binding.photoList.getHeight() - binding.topBar.getHeight());
        float progress = Math.min(1f, Math.max(0f, (scrollY - threshold * 0.7f) / (threshold * 0.3f)));
        binding.topBar.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                color(R.color.bg_app), Math.round(255 * progress)));
        binding.topBar.setElevation(progress >= 1f ? 6 * getResources().getDisplayMetrics().density : 0f);
        binding.topTitle.setAlpha(progress);
        if (pet != null) {
            binding.topTitle.setText(pet.name());
        }
    }

    private void bindPublisher(ViewPublisherCardBinding card, PublicProfile profile, boolean owner) {
        card.getRoot().setVisibility(View.VISIBLE);
        if (profile == null) {
            card.nameView.setText(R.string.publisher_hidden);
            card.metaView.setText("");
            card.bioView.setVisibility(View.GONE);
            card.contactRow.setVisibility(View.GONE);
            card.contactUnavailable.setVisibility(View.GONE);
            card.publisherHeader.setOnClickListener(null);
            return;
        }
        PendingAction guestContact = viewer == null
                ? PendingAction.of(ProtectedAction.CONTACT_OWNER, "petId", petId.toString())
                : null;
        PublisherCards.bind(this, card, profile, owner, getString(R.string.contact_whatsapp_message, pet.name()), true, guestContact);
        boolean chat = !owner && viewer != null;
        card.chatButton.setVisibility(chat ? View.VISIBLE : View.GONE);
        card.chatButton.setOnClickListener(chat ? view -> openOwnerChat(card, profile) : null);
    }

    /** In-app chat with the owner, also when they keep their phone number private. */
    private void openOwnerChat(ViewPublisherCardBinding card, PublicProfile profile) {
        Pet listing = pet;
        card.chatButton.setEnabled(false);
        Ui.run(this, () -> {
            var app = Ui.app(this);
            return app.messaging().openForAdoption(app.auth().requireSession(), listing);
        }, room -> {
            card.chatButton.setEnabled(true);
            Bundle args = new Bundle();
            args.putString("conversationId", room.toString());
            args.putString("withUserId", profile.id().toString());
            args.putString("title", profile.fullName());
            Ui.nav(this).navigate(R.id.action_global_chat, args);
        }, code -> {
            card.chatButton.setEnabled(true);
            Ui.snack(binding.getRoot(), code);
        });
    }

    private void addFact(String text, int background) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setClickable(false);
        chip.setChipBackgroundColor(ColorStateList.valueOf(background));
        chip.setTextColor(color(R.color.text_primary));
        chip.setChipStrokeWidth(0f);
        binding.factChips.addView(chip);
    }

    private int color(int resource) {
        return ContextCompat.getColor(requireContext(), resource);
    }

    private void share() {
        if (pet == null) {
            return;
        }
        String text = getString(R.string.share_text, pet.name(),
                pet.breed().isEmpty() ? Labels.species(requireContext(), pet.species()) : pet.breed(),
                Labels.place(pet), Contact.listingLink(pet.id()));
        Contact.share(requireContext(), text);
    }

    private void apply() {
        if (pet == null) {
            return;
        }
        String message = Ui.text(binding.messageInput).trim();
        binding.messageLayout.setError(null);
        PendingAction intent = PendingAction.of(ProtectedAction.APPLY_ADOPTION, "petId", petId.toString());
        if (!Gate.require(this, message.isEmpty() ? intent : intent.with("draft", message))) {
            return;
        }
        if (message.length() < 10 || message.length() > 2000) {
            binding.messageLayout.setError(getString(R.string.validation_message));
            return;
        }
        binding.applyButton.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        Pet target = pet;
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.auth().requireSession();
            User applicant = app.userService().current(session);
            try {
                app.adoptionService().submit(session, applicant, target, message);
                return "adoption.sent";
            } catch (PersistenceException exception) {
                if (!"network.unavailable".equals(exception.code())) {
                    throw exception;
                }
                SyncOutboxEntry entry = new SyncOutboxEntry();
                entry.operation = OutboxSyncWorker.APPLY_ADOPTION;
                entry.payload = OutboxPolicy.encodeAdoption(target.id(), message);
                entry.createdAtEpochMs = System.currentTimeMillis();
                app.database().outbox().insert(entry);
                OutboxSyncWorker.enqueueNow(app);
                return "sync.offline";
            }
        }, result -> {
            binding.progress.setVisibility(View.INVISIBLE);
            binding.applySection.setVisibility(View.GONE);
            binding.messageView.setText("adoption.sent".equals(result) ? R.string.adoption_sent : R.string.sync_offline);
        }, code -> {
            binding.progress.setVisibility(View.INVISIBLE);
            binding.applyButton.setEnabled(true);
            binding.messageView.setText(Codes.text(code));
        });
    }

    static String memberSince(Context context, PublicProfile profile) {
        if (profile.memberSince() == null) {
            return "";
        }
        String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date.from(profile.memberSince()));
        return context.getString(R.string.publisher_member_since, date);
    }

    private static final class Loaded {
        final Pet pet;
        final AdoptionApplication mine;
        final UUID viewer;
        final boolean favorite;
        final PendingAction resume;
        final PremiumRequest premium;
        /** Drawn from the list's copy while the full listing loads; account-specific parts stay hidden. */
        final boolean partial;

        Loaded(Pet pet, AdoptionApplication mine, UUID viewer, boolean favorite, PendingAction resume, PremiumRequest premium) {
            this(pet, mine, viewer, favorite, resume, premium, false);
        }

        private Loaded(Pet pet, AdoptionApplication mine, UUID viewer, boolean favorite, PendingAction resume,
                       PremiumRequest premium, boolean partial) {
            this.pet = pet;
            this.mine = mine;
            this.viewer = viewer;
            this.favorite = favorite;
            this.resume = resume;
            this.premium = premium;
            this.partial = partial;
        }

        static Loaded partial(Pet pet, UUID viewer) {
            return new Loaded(pet, null, viewer, false, null, null, true);
        }

        /** The copy kept for redrawing after the user comes back: nothing to resume a second time. */
        Loaded kept(boolean favorite) {
            return new Loaded(pet, mine, viewer, favorite, null, premium, false);
        }
    }

    private static final class Publisher {
        final UUID ownerId;
        final PublicProfile profile;
        final List<Pet> others;
        final boolean fresh;

        Publisher(UUID ownerId, PublicProfile profile, List<Pet> others, boolean fresh) {
            this.ownerId = ownerId;
            this.profile = profile;
            this.others = others;
            this.fresh = fresh;
        }
    }
}
