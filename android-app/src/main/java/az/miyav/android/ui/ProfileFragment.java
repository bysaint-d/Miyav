package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentProfileBinding;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.User;
import az.miyav.domain.walk.MyWalks;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingStatus;
import az.miyav.domain.walk.WalkerRating;

import java.math.BigDecimal;
import java.util.List;

/**
 * The signed-in member: who they are, what they earned walking pets, support and the listings they posted.
 * Settings live one tap away.
 */
public class ProfileFragment extends Fragment {
    private static final String STATE_WALKS = "profile_walks";
    private static final String SUPPORT_WHATSAPP = "994503477306";
    private FragmentProfileBinding binding;
    private PetCardAdapter pets;
    private WalkListingAdapter walks;
    private boolean showWalks;
    private Loaded loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        if (savedInstanceState != null) {
            showWalks = savedInstanceState.getBoolean(STATE_WALKS, false);
        }
        pets = new PetCardAdapter(Ui.app(this), true, pet -> {
            Bundle args = new Bundle();
            args.putString("petId", pet.id().toString());
            Ui.nav(this).navigate(R.id.action_home_to_detail, args);
        });
        walks = new WalkListingAdapter(Ui.app(this), listing -> {
            Bundle args = new Bundle();
            args.putString("listingId", listing.id().toString());
            Ui.nav(this).navigate(R.id.action_global_walk_detail, args);
        });
        walks.showStatus(true);
        binding.listingList.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.listingsGroup.check(showWalks ? R.id.walksTab : R.id.adoptionTab);
        binding.listingsGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showWalks = checkedId == R.id.walksTab;
                render();
            }
        });
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnRefreshListener(this::load);
        View.OnClickListener edit = view -> Ui.nav(this).navigate(R.id.action_global_profile_edit);
        binding.editButton.setOnClickListener(edit);
        binding.avatarView.setOnClickListener(edit);
        binding.settingsButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_settings));
        binding.contractButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_payout));
        binding.earningsCard.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_wallet));
        binding.walletButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_wallet));
        binding.applicationsButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_applications));
        binding.supportButton.setOnClickListener(view ->
                Contact.whatsApp(requireContext(), SUPPORT_WHATSAPP, getString(R.string.profile_support_message)));
        binding.newListingButton.setOnClickListener(view -> Ui.nav(this).navigate(showWalks
                ? R.id.action_global_walk_editor : R.id.action_home_to_editor));
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_WALKS, showWalks);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (loaded == null && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.auth().requireSession();
            var user = Ui.async(() -> app.userService().current(session));
            var pets = Ui.async(() -> app.petService().mine(session));
            var walks = Ui.async(() -> app.walkListings().myWalks(session));
            var rating = Ui.async(() -> app.walkListings().walkerRatings(session, List.of(session.userId())).get(session.userId()));
            return new Loaded(Ui.join(user), Ui.join(pets), Ui.join(walks), Ui.join(rating));
        }, result -> {
            binding.refresh.setRefreshing(false);
            loaded = result;
            fill(result.user);
            fillStats(result);
            render();
        }, code -> {
            binding.refresh.setRefreshing(false);
            if (loaded == null) {
                States.message(binding.state, Codes.text(code), this::load);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void fill(User user) {
        binding.nameView.setText(user.fullName());
        String place = user.district().isEmpty() ? user.city() : user.city() + ", " + user.district();
        binding.metaView.setText(place.isEmpty() ? user.email() : getString(R.string.format_pair, user.email(), place));
        binding.bioView.setText(user.bio());
        binding.bioView.setVisibility(user.bio().isEmpty() ? View.GONE : View.VISIBLE);
        Ui.app(this).photos().avatar(binding.avatarView, user);
    }

    /** Earnings and completed jobs count only walks this member did for someone else and that were marked done. */
    private void fillStats(Loaded loaded) {
        BigDecimal earned = BigDecimal.ZERO;
        int completed = 0;
        for (WalkListing listing : loaded.walks) {
            if (listing.status() == WalkListingStatus.COMPLETED && listing.walkedBy(loaded.user.id())) {
                earned = earned.add(listing.effectiveTotal());
                completed++;
            }
        }
        binding.earningsView.setText(getString(R.string.profile_stat_money, Labels.money(earned)));
        binding.completedView.setText(String.valueOf(completed));
        binding.ratingView.setText(loaded.rating == null ? getString(R.string.profile_stat_none)
                : getString(R.string.profile_stat_stars, Labels.rating(loaded.rating.average())));
        binding.ratingCount.setText(loaded.rating == null ? getString(R.string.profile_rating_none)
                : getResources().getQuantityString(R.plurals.profile_rating_count, loaded.rating.count(), loaded.rating.count()));
    }

    private void render() {
        if (loaded == null) {
            return;
        }
        binding.adoptionTab.setText(getString(R.string.profile_tab_adoption, loaded.pets.size()));
        binding.walksTab.setText(getString(R.string.profile_tab_walks, loaded.walks.size()));
        if (showWalks) {
            walks.stages(loaded.myWalks);
            walks.replace(loaded.walks);
            binding.listingList.setAdapter(walks);
        } else {
            pets.replace(loaded.pets);
            binding.listingList.setAdapter(pets);
        }
        boolean empty = showWalks ? loaded.walks.isEmpty() : loaded.pets.isEmpty();
        if (empty) {
            States.message(binding.state, showWalks ? R.string.wl_mine_empty : R.string.empty_pets, null);
        } else {
            States.hide(binding.state);
        }
    }

    private static final class Loaded {
        final User user;
        final List<Pet> pets;
        final MyWalks myWalks;
        final List<WalkListing> walks;
        final WalkerRating rating;

        Loaded(User user, List<Pet> pets, MyWalks myWalks, WalkerRating rating) {
            this.user = user;
            this.pets = pets;
            this.myWalks = myWalks;
            this.walks = myWalks.all();
            this.rating = rating;
        }
    }
}
