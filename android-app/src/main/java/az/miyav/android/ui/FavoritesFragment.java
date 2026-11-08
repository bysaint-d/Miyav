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
import az.miyav.android.databinding.FragmentFavoritesBinding;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.pet.Pet;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Saved pets. Guests keep them on the device; signed-in users see their account favourites. */
public class FavoritesFragment extends Fragment {
    private FragmentFavoritesBinding binding;
    private PetCardAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFavoritesBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        adapter = new PetCardAdapter(Ui.app(this), false, pet -> {
            Bundle args = new Bundle();
            args.putString("petId", pet.id().toString());
            Ui.nav(this).navigate(R.id.action_global_detail, args);
        });
        binding.itemList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.itemList.setAdapter(adapter);
        binding.syncButton.setOnClickListener(view -> Gate.require(this, ProtectedAction.SYNC_FAVORITES));
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.app(this).authState().observe(getViewLifecycleOwner(), state -> {
            MiyavApplication app = Ui.app(this);
            if (app.auth().takePending(ProtectedAction.SYNC_FAVORITES, null, null).isPresent()) {
                app.claimFavoritesOffer();
                syncThenLoad();
            } else {
                load();
            }
        });
    }

    /** The user asked to move device favourites to the account, so no second confirmation is shown. */
    private void syncThenLoad() {
        States.loading(binding.state);
        MiyavApplication app = Ui.app(this);
        Ui.run(this, () -> app.favorites().syncGuestFavorites(app.auth().requireSession()), result -> {
            Ui.snackText(binding.getRoot(), getString(R.string.favorites_synced, result.added(), result.alreadySaved(), result.unavailable()));
            load();
        }, code -> {
            Ui.snack(binding.getRoot(), code);
            load();
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        States.loading(binding.state);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession viewer = app.auth().viewer();
            Set<UUID> ids = app.favorites().ids(viewer);
            List<Pet> pets = app.discovery().listings(ids);
            app.favorites().forgetUnavailable(viewer, ids, pets);
            return new Loaded(pets, viewer == null);
        }, loaded -> {
            binding.guestNote.setVisibility(loaded.guest && !loaded.pets.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.replace(loaded.pets);
            if (loaded.pets.isEmpty()) {
                States.message(binding.state, R.string.favorites_empty, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> States.message(binding.state, Codes.text(code), this::load));
    }

    private static final class Loaded {
        final List<Pet> pets;
        final boolean guest;

        Loaded(List<Pet> pets, boolean guest) {
            this.pets = pets;
            this.guest = guest;
        }
    }
}
