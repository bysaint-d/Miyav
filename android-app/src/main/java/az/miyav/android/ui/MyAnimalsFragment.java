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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentMyAnimalsBinding;
import az.miyav.android.databinding.ItemMyPetBinding;
import az.miyav.domain.pet.PetProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** "Mənim heyvanlarım": the member's own pets, chosen from when a walk starts. */
public class MyAnimalsFragment extends Fragment {
    private FragmentMyAnimalsBinding binding;
    private Adapter adapter;
    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMyAnimalsBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        adapter = new Adapter(Ui.app(this), pet -> PetEditorSheet.open(getChildFragmentManager(), pet));
        binding.itemList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.itemList.setAdapter(adapter);
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnChildScrollUpCallback((parent, child) -> binding.itemList.canScrollVertically(-1));
        binding.refresh.setOnRefreshListener(this::load);
        binding.addButton.setOnClickListener(view -> PetEditorSheet.open(getChildFragmentManager(), null));
        getChildFragmentManager().setFragmentResultListener(PetEditorSheet.RESULT, getViewLifecycleOwner(), (key, result) -> load());
        load();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (!loaded && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.myPets().mine(app.auth().requireSession());
        }, pets -> {
            loaded = true;
            binding.refresh.setRefreshing(false);
            adapter.replace(pets);
            if (pets.isEmpty()) {
                States.message(binding.state, R.string.my_pets_empty, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> {
            binding.refresh.setRefreshing(false);
            States.message(binding.state, Codes.text(code), this::load);
        });
    }

    static String meta(Context context, PetProfile pet) {
        String line = Labels.speciesEmoji(pet.species()) + "  " + Labels.petMeta(context, pet.species(), pet.breed(), pet.ageYears());
        return pet.weightKg() == null ? line : line + "  ·  " + context.getString(R.string.pet_kg, Labels.money(pet.weightKg()));
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final MiyavApplication app;
        private final List<PetProfile> pets = new ArrayList<>();
        private final Consumer<PetProfile> onClick;

        Adapter(MiyavApplication app, Consumer<PetProfile> onClick) {
            this.app = app;
            this.onClick = onClick;
        }

        void replace(List<PetProfile> next) {
            pets.clear();
            pets.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemMyPetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PetProfile pet = pets.get(position);
            Context context = holder.itemView.getContext();
            holder.binding.nameView.setText(pet.name());
            holder.binding.metaView.setText(meta(context, pet));
            holder.binding.photoView.setBackgroundTintList(ColorStateList.valueOf(Labels.speciesColor(context, pet.species())));
            if (pet.photoPath() == null) {
                holder.binding.photoView.setTag(R.id.photo_tag, null);
                holder.binding.photoView.setImageDrawable(null);
                holder.binding.emojiView.setVisibility(View.VISIBLE);
                holder.binding.emojiView.setText(Labels.speciesEmoji(pet.species()));
            } else {
                holder.binding.emojiView.setVisibility(View.GONE);
                app.photos().pet(holder.binding.photoView, pet.photoPath());
            }
            holder.itemView.setOnClickListener(view -> onClick.accept(pet));
        }

        @Override
        public int getItemCount() {
            return pets.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemMyPetBinding binding;

            Holder(ItemMyPetBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
