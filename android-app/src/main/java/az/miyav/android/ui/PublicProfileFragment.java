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
import az.miyav.android.databinding.FragmentPublicProfileBinding;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.PublicProfile;

import java.util.List;
import java.util.UUID;

public class PublicProfileFragment extends Fragment {
    private FragmentPublicProfileBinding binding;
    private PetCardAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPublicProfileBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        adapter = new PetCardAdapter(Ui.app(this), false, pet -> {
            Bundle args = new Bundle();
            args.putString("petId", pet.id().toString());
            Ui.nav(this).navigate(R.id.action_global_detail, args);
        });
        binding.listingList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listingList.setAdapter(adapter);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        UUID userId = UUID.fromString(requireArguments().getString("userId"));
        Ui.app(this).authState().observe(getViewLifecycleOwner(), state -> load(userId));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load(UUID userId) {
        binding.skeleton.setVisibility(View.VISIBLE);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession viewer = app.auth().viewer();
            PublicProfile profile = app.discovery().publisher(viewer, userId).orElse(null);
            List<Pet> listings = profile == null ? List.of() : app.discovery().byPublisher(viewer, userId);
            boolean self = viewer != null && userId.equals(viewer.userId());
            if (viewer != null) {
                app.auth().takePending(ProtectedAction.CONTACT_OWNER, "userId", userId.toString());
            }
            return new Loaded(profile, listings, self, viewer == null);
        }, loaded -> {
            binding.skeleton.setVisibility(View.GONE);
            if (loaded.profile == null) {
                States.message(binding.state, R.string.publisher_hidden, null);
                return;
            }
            binding.content.setVisibility(View.VISIBLE);
            binding.toolbar.setTitle(loaded.profile.fullName());
            PublisherCards.bind(this, binding.publisher, loaded.profile, loaded.self,
                    getString(R.string.contact_walk_message), false,
                    loaded.guest ? PendingAction.of(ProtectedAction.CONTACT_OWNER, "userId", userId.toString()) : null);
            adapter.replace(loaded.listings);
            binding.listingsEmpty.setVisibility(loaded.listings.isEmpty() ? View.VISIBLE : View.GONE);
        }, code -> {
            binding.skeleton.setVisibility(View.GONE);
            States.message(binding.state, Codes.text(code), () -> load(userId));
        });
    }

    private static final class Loaded {
        final PublicProfile profile;
        final List<Pet> listings;
        final boolean self;
        final boolean guest;

        Loaded(PublicProfile profile, List<Pet> listings, boolean self, boolean guest) {
            this.profile = profile;
            this.listings = listings;
            this.self = self;
            this.guest = guest;
        }
    }
}
