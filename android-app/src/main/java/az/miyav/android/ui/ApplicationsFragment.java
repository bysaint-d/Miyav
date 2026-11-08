package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentListBinding;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class ApplicationsFragment extends Fragment implements ApplicationAdapter.Actions {
    private FragmentListBinding binding;
    private ApplicationAdapter adapter;
    private Snapshot snapshot;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentListBinding.inflate(inflater, container, false);
        binding.titleView.setText(R.string.nav_applications);
        binding.tabGroup.setVisibility(View.VISIBLE);
        binding.tabFirst.setText(R.string.adoption_tab_mine);
        binding.tabSecond.setText(R.string.adoption_tab_incoming);
        binding.tabGroup.check(R.id.tabFirst);
        binding.tabGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                render();
            }
        });
        adapter = new ApplicationAdapter(this);
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
        if (snapshot == null && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.auth().requireSession();
            Map<UUID, String> titles = new HashMap<>();
            List<AdoptionApplication> incoming = new ArrayList<>();
            for (Pet pet : app.petService().mine(session)) {
                titles.put(pet.id(), pet.name());
                incoming.addAll(app.adoptionService().forPet(session, pet));
            }
            List<AdoptionApplication> mine = app.adoptionService().mine(session);
            for (AdoptionApplication application : mine) {
                if (!titles.containsKey(application.petId())) {
                    Optional<Pet> pet = app.petService().find(application.petId(), session);
                    pet.ifPresent(found -> titles.put(found.id(), found.name()));
                }
            }
            incoming.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));
            return new Snapshot(mine, incoming, titles);
        }, result -> {
            snapshot = result;
            binding.refresh.setRefreshing(false);
            render();
        }, code -> {
            binding.refresh.setRefreshing(false);
            if (snapshot == null) {
                States.message(binding.state, Codes.text(code), this::load);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void render() {
        if (snapshot == null || binding == null) {
            return;
        }
        boolean incoming = binding.tabGroup.getCheckedButtonId() == R.id.tabSecond;
        List<AdoptionApplication> rows = incoming ? snapshot.incoming : snapshot.mine;
        adapter.replace(rows, snapshot.titles, incoming);
        if (rows.isEmpty()) {
            States.message(binding.state, R.string.empty_applications, null);
        } else {
            States.hide(binding.state);
        }
    }

    @Override
    public void accept(AdoptionApplication application) {
        confirm(R.string.adoption_accept_confirm, R.string.adoption_accept, (session) -> decide(session, application, true));
    }

    @Override
    public void reject(AdoptionApplication application) {
        confirm(R.string.adoption_reject_confirm, R.string.adoption_reject, (session) -> decide(session, application, false));
    }

    @Override
    public void cancel(AdoptionApplication application) {
        confirm(R.string.adoption_cancel_confirm, R.string.adoption_cancel, (session) -> {
            MiyavApplication app = Ui.app(this);
            User actor = app.userService().current(session);
            return app.adoptionService().cancel(session, actor, application);
        });
    }

    @Override
    public void open(AdoptionApplication application) {
        Bundle args = new Bundle();
        args.putString("petId", application.petId().toString());
        Ui.nav(this).navigate(R.id.action_home_to_detail, args);
    }

    private AdoptionApplication decide(AuthSession session, AdoptionApplication application, boolean accept) {
        MiyavApplication app = Ui.app(this);
        User actor = app.userService().current(session);
        Pet pet = app.petService().find(application.petId(), session)
                .orElseThrow(() -> new az.miyav.common.error.ValidationException("adoption.listing_unavailable"));
        return accept
                ? app.adoptionService().accept(session, actor, pet, application)
                : app.adoptionService().reject(session, actor, pet, application);
    }

    private void confirm(int message, int action, Function<AuthSession, AdoptionApplication> work) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(message)
                .setPositiveButton(action, (dialog, which) -> Ui.run(this,
                        () -> work.apply(Ui.app(this).auth().requireSession()),
                        updated -> {
                            Ui.snack(binding.getRoot(), R.string.adoption_updated);
                            load();
                        },
                        code -> Ui.snack(binding.getRoot(), code)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static final class Snapshot {
        final List<AdoptionApplication> mine;
        final List<AdoptionApplication> incoming;
        final Map<UUID, String> titles;

        Snapshot(List<AdoptionApplication> mine, List<AdoptionApplication> incoming, Map<UUID, String> titles) {
            this.mine = mine;
            this.incoming = incoming;
            this.titles = titles;
        }
    }
}
