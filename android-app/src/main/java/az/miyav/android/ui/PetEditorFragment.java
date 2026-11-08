package az.miyav.android.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentPetEditorBinding;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.pet.Vaccination;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PetEditorFragment extends Fragment {
    private FragmentPetEditorBinding binding;
    private PhotoAdapter photos;
    private Choice<PetSpecies> species;
    private Choice<Vaccination> vaccination;
    private Choice<String> city;
    private Pet current;
    private ListingLocation location;
    private Form snapshot;
    private boolean loaded;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPetEditorBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        species = new Choice<>(binding.speciesInput, Arrays.asList(PetSpecies.values()),
                value -> Labels.speciesEmoji(value) + "  " + Labels.species(requireContext(), value), PetSpecies.DOG);
        vaccination = new Choice<>(binding.vaccinationInput, Arrays.asList(Vaccination.values()),
                value -> Labels.vaccination(requireContext(), value), Vaccination.UNKNOWN);
        city = new Choice<>(binding.cityInput, Labels.CITIES, value -> value, Labels.CITIES.get(0));
        photos = new PhotoAdapter(Ui.app(this), this::removePhoto);
        binding.photoList.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        binding.photoList.setAdapter(photos);
        binding.genderGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                binding.genderError.setVisibility(View.GONE);
            }
        });
        binding.photoButton.setOnClickListener(view -> {
            if (fieldsValid()) {
                picker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            }
        });
        binding.locationButton.setOnClickListener(view -> openPicker());
        binding.saveButton.setOnClickListener(view -> save(false));
        binding.publishButton.setOnClickListener(view -> save(true));
        binding.archiveButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.pet_archive_confirm)
                .setPositiveButton(R.string.pet_archive, (dialog, which) -> archive())
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        getParentFragmentManager().setFragmentResultListener(LocationPickerFragment.RESULT, getViewLifecycleOwner(), (key, result) -> {
            location = new ListingLocation(result.getDouble(LocationPickerFragment.LATITUDE),
                    result.getDouble(LocationPickerFragment.LONGITUDE), result.getString(LocationPickerFragment.NAME));
            renderLocation();
        });
        if (snapshot != null) {
            fill(snapshot);
            render();
        } else {
            render();
            String petId = requireArguments().getString("petId", "");
            if (petId != null && !petId.isBlank() && !loaded) {
                load(UUID.fromString(petId));
            }
        }
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        snapshot = form();
        binding = null;
        super.onDestroyView();
    }

    private void load(UUID petId) {
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.petService().find(petId, app.authService().requireSession()).orElse(null);
        }, pet -> {
            busy(false);
            loaded = true;
            if (pet == null) {
                binding.messageView.setText(R.string.listing_unavailable);
                return;
            }
            current = pet;
            location = pet.location();
            fill(new Form(pet.species(), pet.name(), pet.breed(), String.valueOf(pet.ageMonths()), pet.gender(),
                    pet.vaccination(), pet.city(), pet.description()));
            render();
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void fill(Form form) {
        binding.nameInput.setText(form.name);
        binding.breedInput.setText(form.breed);
        binding.ageInput.setText(form.ageText);
        binding.descriptionInput.setText(form.description);
        species.select(form.species);
        vaccination.select(form.vaccination);
        city.select(form.city);
        if (form.gender == PetGender.MALE) {
            binding.genderGroup.check(R.id.genderMale);
        } else if (form.gender == PetGender.FEMALE) {
            binding.genderGroup.check(R.id.genderFemale);
        } else {
            binding.genderGroup.clearChecked();
        }
    }

    private void render() {
        boolean existing = current != null;
        binding.toolbar.setTitle(existing ? R.string.pet_edit : R.string.pet_create);
        int count = existing ? current.photoPaths().size() : 0;
        binding.photoCount.setText(getString(R.string.pet_photos_count, count));
        photos.replace(existing ? current.photoPaths() : List.of());
        binding.photoList.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        binding.photoButton.setEnabled(count < 5);
        PetStatus status = existing ? current.status() : PetStatus.DRAFT;
        binding.publishButton.setVisibility(status == PetStatus.DRAFT || status == PetStatus.ARCHIVED ? View.VISIBLE : View.GONE);
        binding.saveButton.setText(status == PetStatus.DRAFT ? R.string.pet_save_draft : R.string.profile_save);
        binding.archiveButton.setVisibility(existing && status != PetStatus.ARCHIVED ? View.VISIBLE : View.GONE);
        renderLocation();
    }

    private void renderLocation() {
        if (binding == null) {
            return;
        }
        if (location == null) {
            binding.locationValue.setText(R.string.pet_location_none);
            binding.locationButton.setText(R.string.pet_location_choose);
        } else {
            binding.locationValue.setText(location.name().isEmpty()
                    ? String.format(java.util.Locale.US, "%.3f, %.3f", location.latitude(), location.longitude())
                    : location.name());
            binding.locationButton.setText(R.string.pet_location_change);
        }
    }

    private void openPicker() {
        Bundle args = new Bundle();
        if (location != null) {
            args.putDouble(LocationPickerFragment.LATITUDE, location.latitude());
            args.putDouble(LocationPickerFragment.LONGITUDE, location.longitude());
        }
        Ui.nav(this).navigate(R.id.action_global_location_picker, args);
    }

    private PetGender gender() {
        int checked = binding.genderGroup.getCheckedButtonId();
        if (checked == R.id.genderMale) {
            return PetGender.MALE;
        }
        if (checked == R.id.genderFemale) {
            return PetGender.FEMALE;
        }
        return PetGender.UNKNOWN;
    }

    private boolean fieldsValid() {
        binding.nameLayout.setError(null);
        binding.ageLayout.setError(null);
        binding.descriptionLayout.setError(null);
        binding.genderError.setVisibility(View.GONE);
        boolean valid = true;
        String name = Ui.text(binding.nameInput).trim();
        if (name.isEmpty() || name.length() > 80) {
            binding.nameLayout.setError(getString(R.string.validation_pet_name));
            valid = false;
        }
        int age = age(Ui.text(binding.ageInput));
        if (age < 0 || age > 1200) {
            binding.ageLayout.setError(getString(R.string.validation_age));
            valid = false;
        }
        if (Ui.text(binding.descriptionInput).trim().length() < 10) {
            binding.descriptionLayout.setError(getString(R.string.validation_description));
            valid = false;
        }
        boolean legacyUnknown = current != null && current.gender() == PetGender.UNKNOWN;
        if (gender() == PetGender.UNKNOWN && !legacyUnknown) {
            binding.genderError.setVisibility(View.VISIBLE);
            valid = false;
        }
        return valid;
    }

    private static int age(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private Pet persist(AuthSession session, MiyavApplication app, Form form, ListingLocation place) {
        int age = age(form.ageText);
        if (current == null) {
            return app.petService().createDraft(session, form.species, form.name, form.breed, age, form.gender,
                    form.city, form.description, form.vaccination, place);
        }
        PetStatus status = current.status() == PetStatus.ARCHIVED ? PetStatus.DRAFT : current.status();
        return app.petService().update(session, current, form.name, form.breed, age, form.gender, form.city,
                form.description, status, form.vaccination, place);
    }

    private Form form() {
        return new Form(
                species.value(),
                Ui.text(binding.nameInput).trim(),
                Ui.text(binding.breedInput).trim(),
                Ui.text(binding.ageInput).trim(),
                gender(),
                vaccination.value(),
                city.value(),
                Ui.text(binding.descriptionInput).trim()
        );
    }

    private void save(boolean publish) {
        if (!fieldsValid()) {
            return;
        }
        if (publish && (current == null || current.photoPaths().isEmpty())) {
            binding.messageView.setText(R.string.pet_photo_required);
            return;
        }
        if (publish && location == null) {
            binding.messageView.setText(R.string.pet_location_required);
            return;
        }
        Form form = form();
        ListingLocation place = location;
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.authService().requireSession();
            Pet saved = persist(session, app, form, place);
            return publish ? app.petService().publish(session, saved) : saved;
        }, pet -> {
            busy(false);
            current = pet;
            render();
            if (publish) {
                Ui.snack(binding.getRoot(), R.string.pet_published);
                Ui.nav(this).popBackStack();
            } else {
                binding.messageView.setText(R.string.pet_saved);
            }
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void onPicked(@Nullable Uri uri) {
        if (uri == null || binding == null) {
            return;
        }
        Form form = form();
        ListingLocation place = location;
        Context context = requireContext().getApplicationContext();
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.authService().requireSession();
            Pet pet = current == null ? persist(session, app, form, place) : current;
            byte[] jpeg = JpegEncoder.fromUri(context, uri, 1600);
            return app.petService().addPhoto(session, pet, jpeg);
        }, pet -> {
            busy(false);
            current = pet;
            render();
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void removePhoto(String path) {
        if (current == null) {
            return;
        }
        Pet target = current;
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.petService().removePhoto(app.authService().requireSession(), target, path);
        }, pet -> {
            busy(false);
            current = pet;
            render();
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void archive() {
        if (current == null) {
            return;
        }
        Pet target = current;
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.petService().archive(app.authService().requireSession(), target);
        }, pet -> {
            busy(false);
            Ui.snack(binding.getRoot(), R.string.pet_archived);
            Ui.nav(this).popBackStack(R.id.homeFragment, false);
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.saveButton.setEnabled(!busy);
        binding.publishButton.setEnabled(!busy);
        binding.archiveButton.setEnabled(!busy);
        binding.locationButton.setEnabled(!busy);
        binding.photoButton.setEnabled(!busy && (current == null || current.photoPaths().size() < 5));
    }

    private static final class Form {
        final PetSpecies species;
        final String name;
        final String breed;
        final String ageText;
        final PetGender gender;
        final Vaccination vaccination;
        final String city;
        final String description;

        Form(PetSpecies species, String name, String breed, String ageText, PetGender gender,
             Vaccination vaccination, String city, String description) {
            this.species = species;
            this.name = name;
            this.breed = breed;
            this.ageText = ageText;
            this.gender = gender;
            this.vaccination = vaccination;
            this.city = city;
            this.description = description;
        }
    }
}
