package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentListBinding;
import az.miyav.domain.pet.Pet;

public class MyPetsFragment extends Fragment {
    private FragmentListBinding binding;
    private PetCardAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentListBinding.inflate(inflater, container, false);
        binding.titleView.setText(R.string.nav_pets);
        adapter = new PetCardAdapter(Ui.app(this), true, this::open);
        binding.itemList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.itemList.setAdapter(adapter);
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnChildScrollUpCallback((parent, child) -> binding.itemList.canScrollVertically(-1));
        binding.refresh.setOnRefreshListener(this::load);
        binding.primaryButton.setVisibility(View.VISIBLE);
        binding.primaryButton.setOnClickListener(view -> {
            Bundle args = new Bundle();
            args.putString("petId", "");
            Ui.nav(this).navigate(R.id.action_home_to_editor, args);
        });
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
        if (adapter.isEmpty() && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            var app = Ui.app(this);
            return app.petService().mine(app.authService().requireSession());
        }, pets -> {
            binding.refresh.setRefreshing(false);
            adapter.replace(pets);
            if (pets.isEmpty()) {
                States.message(binding.state, R.string.empty_pets, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> {
            binding.refresh.setRefreshing(false);
            if (adapter.isEmpty()) {
                States.message(binding.state, Codes.text(code), this::load);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void open(Pet pet) {
        Bundle args = new Bundle();
        args.putString("petId", pet.id().toString());
        Ui.nav(this).navigate(R.id.action_home_to_detail, args);
    }
}
