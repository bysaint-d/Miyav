package az.miyav.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.ItemWalkJobBinding;
import az.miyav.android.databinding.SheetWalkStartBinding;
import az.miyav.application.WalkingService;
import az.miyav.common.error.AppException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Kimi gəzdirirsən?": tick own pets (or add one on the spot) and/or one walk job the user was chosen
 * for, then start. Reports {@link #RESULT} to the walk screen, which handles permissions and location.
 */
public class WalkStartSheet extends BottomSheetDialogFragment {
    static final String RESULT = "walk_start_result";
    static final String PETS = "pets";
    static final String LISTING = "listing";
    static final String MESSAGE = "message";
    private static final Duration JOB_WINDOW = Duration.ofHours(12);
    private SheetWalkStartBinding binding;
    private PetTileAdapter adapter;
    private final Set<Object> chosen = new LinkedHashSet<>();
    private UUID job;
    private UUID justAdded;
    private List<WalkListing> jobs = List.of();

    static void open(FragmentManager manager) {
        new WalkStartSheet().show(manager, "walk-start");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetWalkStartBinding.inflate(inflater, container, false);
        adapter = new PetTileAdapter(app(), this::toggle, () -> PetEditorSheet.open(getChildFragmentManager(), null));
        binding.petList.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        binding.petList.setAdapter(adapter);
        binding.manageButton.setOnClickListener(view -> {
            dismiss();
            Ui.nav(requireParentFragment()).navigate(R.id.action_global_my_pets);
        });
        binding.startButton.setOnClickListener(view -> start());
        getChildFragmentManager().setFragmentResultListener(PetEditorSheet.RESULT, this, (key, result) -> {
            if (!result.getBoolean(PetEditorSheet.DELETED)) {
                justAdded = UUID.fromString(result.getString(PetEditorSheet.PET_ID));
            }
            load();
        });
        load();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private MiyavApplication app() {
        return (MiyavApplication) requireActivity().getApplication();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        MiyavApplication app = app();
        View root = binding.getRoot();
        app.io().execute(() -> {
            List<PetProfile> pets = List.of();
            List<WalkListing> mine = List.of();
            String failure = null;
            try {
                AuthSession session = app.auth().requireSession();
                pets = app.myPets().mine(session);
                Instant now = Instant.now();
                List<WalkListing> assigned = new ArrayList<>();
                for (WalkListing listing : app.walkListings().mine(session)) {
                    if (listing.walkedBy(session.userId()) && listing.status() == WalkListingStatus.ASSIGNED
                            && listing.details().startsAt().isBefore(now.plus(JOB_WINDOW))
                            && listing.details().startsAt().isAfter(now.minus(JOB_WINDOW))) {
                        assigned.add(listing);
                    }
                }
                mine = assigned;
            } catch (AppException exception) {
                failure = exception.code();
            }
            List<PetProfile> loadedPets = pets;
            List<WalkListing> loadedJobs = mine;
            String error = failure;
            root.post(() -> {
                if (binding == null) {
                    return;
                }
                binding.progress.setVisibility(View.INVISIBLE);
                if (error != null) {
                    Ui.snack(binding.getRoot(), error);
                }
                show(loadedPets, loadedJobs);
            });
        });
    }

    private void show(List<PetProfile> pets, List<WalkListing> loadedJobs) {
        Context context = requireContext();
        List<PetTileAdapter.Tile> tiles = new ArrayList<>();
        Set<Object> ids = new LinkedHashSet<>();
        for (PetProfile pet : pets) {
            ids.add(pet.id());
            tiles.add(new PetTileAdapter.Tile(pet.id(), pet.name(),
                    Labels.petMeta(context, pet.species(), pet.breed(), pet.ageYears()),
                    pet.species(), pet.photoPath(), false));
        }
        chosen.retainAll(ids);
        if (justAdded != null && ids.contains(justAdded) && chosen.size() < WalkingService.MAX_PETS) {
            chosen.add(justAdded);
        }
        justAdded = null;
        if (pets.size() == 1 && chosen.isEmpty() && loadedJobs.isEmpty()) {
            chosen.add(pets.get(0).id());
        }
        adapter.replace(tiles);
        adapter.setSelected(chosen);
        jobs = loadedJobs;
        renderJobs();
        renderButton();
    }

    private void toggle(PetTileAdapter.Tile tile) {
        if (!chosen.remove(tile.key)) {
            if (chosen.size() >= WalkingService.MAX_PETS) {
                Ui.snack(binding.getRoot(), R.string.walk_pet_limit);
                return;
            }
            chosen.add(tile.key);
        }
        adapter.setSelected(chosen);
        renderButton();
    }

    private void renderJobs() {
        Context context = requireContext();
        binding.jobSection.setVisibility(jobs.isEmpty() ? View.GONE : View.VISIBLE);
        binding.jobList.removeAllViews();
        for (WalkListing listing : jobs) {
            ItemWalkJobBinding item = ItemWalkJobBinding.inflate(getLayoutInflater(), binding.jobList, false);
            boolean selected = listing.id().equals(job);
            item.emojiView.setText(Labels.speciesEmoji(listing.details().species()));
            item.emojiView.setBackgroundTintList(ColorStateList.valueOf(Labels.speciesColor(context, listing.details().species())));
            item.titleView.setText(listing.details().petName());
            item.metaView.setText(Labels.date(listing.details().startsAt()) + "  ·  "
                    + Labels.duration(context, listing.details().durationMinutes()) + "  ·  "
                    + getString(R.string.wl_per_hour, Labels.money(listing.effectivePricePerHour())));
            item.card.setChecked(selected);
            item.card.setStrokeWidth(selected ? Math.round(context.getResources().getDisplayMetrics().density * 2) : 0);
            item.checkView.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            item.card.setOnClickListener(view -> {
                job = listing.id().equals(job) ? null : listing.id();
                renderJobs();
                renderButton();
            });
            binding.jobList.addView(item.getRoot());
        }
    }

    private void renderButton() {
        int count = chosen.size() + (job == null ? 0 : 1);
        binding.startButton.setEnabled(count > 0);
        if (count == 0) {
            binding.startButton.setText(R.string.walk_start_none);
        } else {
            binding.startButton.setText(getString(R.string.walk_start_button_count, count));
        }
    }

    private void start() {
        ArrayList<String> pets = new ArrayList<>();
        for (Object id : chosen) {
            pets.add(id.toString());
        }
        Bundle result = new Bundle();
        result.putStringArrayList(PETS, pets);
        result.putString(LISTING, job == null ? "" : job.toString());
        result.putString(MESSAGE, Ui.text(binding.messageInput).trim());
        getParentFragmentManager().setFragmentResult(RESULT, result);
        dismiss();
    }
}
