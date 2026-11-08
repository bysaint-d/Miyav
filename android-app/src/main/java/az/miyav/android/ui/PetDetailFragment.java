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
import az.miyav.common.error.PersistenceException;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.pet.Vaccination;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.user.User;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PetDetailFragment extends Fragment {
    private FragmentPetDetailBinding binding;
    private PhotoAdapter photos;
    private MiniListingAdapter others;
    private UUID petId;
    private Pet pet;
    private UUID viewer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPetDetailBinding.inflate(inflater, container, false);
        petId = UUID.fromString(requireArguments().getString("petId"));
        binding.backButton.setOnClickListener(view -> Ui.nav(this).popBackStack());
        photos = new PhotoAdapter(Ui.app(this), null);
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
            Bundle args = new Bundle();
            args.putString("petId", petId.toString());
            Ui.nav(this).navigate(R.id.action_detail_to_editor, args);
        });
        binding.shareButton.setOnClickListener(view -> share());
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
    public void onResume() {
        super.onResume();
        binding.mapPreview.onResume();
        load();
    }

    @Override
    public void onPause() {
        binding.mapPreview.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        binding.mapPreview.onDetach();
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (pet == null) {
            binding.skeleton.setVisibility(View.VISIBLE);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.authService().requireSession();
            Pet found = app.petService().find(petId, session).orElse(null);
            AdoptionApplication mine = null;
            if (found != null && !found.ownerId().equals(session.userId())) {
                for (AdoptionApplication application : app.adoptionService().mine(session)) {
                    if (application.petId().equals(petId) && application.status() != ApplicationStatus.CANCELLED) {
                        mine = application;
                        break;
                    }
                }
            }
            return new Loaded(found, mine, session.userId());
        }, this::show, code -> {
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
        Context context = requireContext();
        States.hide(binding.state);
        binding.content.setVisibility(View.VISIBLE);
        binding.titleView.setText(pet.name());
        binding.statusChip.setText(Labels.status(context, pet.status()));
        binding.locationLine.setText(Labels.place(pet));
        binding.factChips.removeAllViews();
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
                    color(pet.vaccination() == Vaccination.YES ? R.color.pastel_mint : R.color.surface_muted));
        }
        binding.bodyView.setText(pet.description());
        binding.careView.setText(Labels.care(context, pet));
        photos.replace(pet.photoPaths());
        binding.photoCounter.setVisibility(pet.photoPaths().size() > 1 ? View.VISIBLE : View.GONE);
        binding.photoCounter.setText(getString(R.string.format_counter, 1, Math.max(1, pet.photoPaths().size())));
        showLocation(pet.location());
        boolean owner = pet.ownerId().equals(loaded.viewer);
        binding.shareButton.setVisibility(pet.status() == PetStatus.PUBLISHED ? View.VISIBLE : View.GONE);
        binding.ownerBadge.setVisibility(owner ? View.VISIBLE : View.GONE);
        binding.editButton.setVisibility(owner && pet.status() != PetStatus.ARCHIVED ? View.VISIBLE : View.GONE);
        boolean canApply = !owner && pet.status() == PetStatus.PUBLISHED && loaded.mine == null;
        binding.applySection.setVisibility(canApply ? View.VISIBLE : View.GONE);
        if (loaded.mine != null) {
            binding.messageView.setText(getString(R.string.format_label_value,
                    getString(R.string.nav_applications), Labels.status(context, loaded.mine.status())));
        } else if (!owner && pet.status() != PetStatus.PUBLISHED) {
            binding.messageView.setText(R.string.adoption_listing_unavailable);
        } else {
            binding.messageView.setText("");
        }
        loadPublisher(pet.ownerId(), owner);
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

    private void loadPublisher(UUID ownerId, boolean owner) {
        binding.publisherSkeleton.setVisibility(View.VISIBLE);
        binding.publisher.getRoot().setVisibility(View.GONE);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.authService().requireSession();
            Optional<PublicProfile> profile = app.userService().publicProfile(session, ownerId);
            List<Pet> more = new ArrayList<>();
            for (Pet other : app.petService().byPublisher(session, ownerId)) {
                if (!other.id().equals(petId)) {
                    more.add(other);
                }
            }
            return new Publisher(profile.orElse(null), more);
        }, publisher -> {
            binding.publisherSkeleton.setVisibility(View.GONE);
            bindPublisher(binding.publisher, publisher.profile, owner);
            others.replace(publisher.others);
            binding.otherSection.setVisibility(publisher.others.isEmpty() ? View.GONE : View.VISIBLE);
        }, code -> {
            binding.publisherSkeleton.setVisibility(View.GONE);
            bindPublisher(binding.publisher, null, owner);
        });
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
        PublisherCards.bind(this, card, profile, owner, getString(R.string.contact_whatsapp_message, pet.name()), true);
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
        if (message.length() < 10 || message.length() > 2000) {
            binding.messageLayout.setError(getString(R.string.validation_message));
            return;
        }
        binding.applyButton.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        Pet target = pet;
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.authService().requireSession();
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

        Loaded(Pet pet, AdoptionApplication mine, UUID viewer) {
            this.pet = pet;
            this.mine = mine;
            this.viewer = viewer;
        }
    }

    private static final class Publisher {
        final PublicProfile profile;
        final List<Pet> others;

        Publisher(PublicProfile profile, List<Pet> others) {
            this.profile = profile;
            this.others = others;
        }
    }
}
