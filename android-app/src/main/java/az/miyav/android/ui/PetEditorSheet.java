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
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.SheetPetEditorBinding;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.pet.PetProfileDraft;
import az.miyav.domain.pet.PetSpecies;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

/**
 * Adds or edits one of the member's own pets. The photo can be picked before the first save: the pet is
 * saved first so the photo has somewhere to go. Reports {@link #RESULT} with the pet id to the caller.
 */
public class PetEditorSheet extends BottomSheetDialogFragment {
    static final String RESULT = "pet_editor_result";
    static final String PET_ID = "petId";
    static final String DELETED = "deleted";
    private SheetPetEditorBinding binding;
    private Choice<PetSpecies> species;
    private PetProfile current;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onPicked);

    /** Opens the editor; {@code pet} null adds a new pet. */
    static void open(FragmentManager manager, PetProfile pet) {
        PetEditorSheet sheet = new PetEditorSheet();
        sheet.current = pet;
        sheet.show(manager, "pet-editor");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetPetEditorBinding.inflate(inflater, container, false);
        species = new Choice<>(binding.speciesInput, Arrays.asList(PetSpecies.values()),
                value -> Labels.speciesEmoji(value) + "  " + Labels.species(requireContext(), value), PetSpecies.DOG);
        species.onChange(this::renderPhoto);
        binding.photoButton.setOnClickListener(view -> picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.saveButton.setOnClickListener(view -> save(null));
        binding.deleteButton.setOnClickListener(view -> confirmDelete());
        if (current != null) {
            binding.titleView.setText(R.string.pet_edit_title);
            binding.nameInput.setText(current.name());
            species.select(current.species());
            binding.breedInput.setText(current.breed());
            binding.ageInput.setText(current.ageYears() == null ? "" : Labels.money(current.ageYears()));
            binding.weightInput.setText(current.weightKg() == null ? "" : Labels.money(current.weightKg()));
            binding.notesInput.setText(current.careNotes());
            binding.deleteButton.setVisibility(View.VISIBLE);
        } else if (savedInstanceState != null) {
            dismissAllowingStateLoss();
        }
        renderPhoto();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void renderPhoto() {
        if (binding == null) {
            return;
        }
        String path = current == null ? null : current.photoPath();
        binding.photoButton.setText(path == null ? R.string.pet_photo_set : R.string.pet_photo_change);
        binding.photoView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Labels.speciesColor(requireContext(), species.value())));
        if (path == null) {
            binding.photoView.setImageDrawable(null);
            binding.emojiView.setVisibility(View.VISIBLE);
            binding.emojiView.setText(Labels.speciesEmoji(species.value()));
        } else {
            binding.emojiView.setVisibility(View.GONE);
            app().photos().pet(binding.photoView, path);
        }
    }

    private MiyavApplication app() {
        return (MiyavApplication) requireActivity().getApplication();
    }

    private static BigDecimal number(String text) {
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Builds the draft and marks the field with the problem; null when invalid. */
    private PetProfileDraft draft() {
        binding.nameLayout.setError(null);
        binding.breedLayout.setError(null);
        binding.ageLayout.setError(null);
        binding.weightLayout.setError(null);
        binding.notesLayout.setError(null);
        try {
            return new PetProfileDraft(Ui.text(binding.nameInput), species.value(), Ui.text(binding.breedInput),
                    number(Ui.text(binding.ageInput)), number(Ui.text(binding.weightInput)), Ui.text(binding.notesInput));
        } catch (ValidationException exception) {
            String message = getString(Codes.text(exception.code()));
            switch (exception.code()) {
                case "petprofile.name" -> binding.nameLayout.setError(message);
                case "petprofile.breed" -> binding.breedLayout.setError(message);
                case "petprofile.age" -> binding.ageLayout.setError(message);
                case "petprofile.weight" -> binding.weightLayout.setError(message);
                default -> binding.notesLayout.setError(message);
            }
            return null;
        }
    }

    /** Saves the form; with a photo it uploads it right after, so a new pet gets its photo in one step. */
    private void save(byte[] photo) {
        PetProfileDraft draft = draft();
        if (draft == null) {
            return;
        }
        UUID id = current == null ? null : current.id();
        String oldPhoto = current == null ? null : current.photoPath();
        busy(true);
        MiyavApplication app = app();
        View root = binding.getRoot();
        app.io().execute(() -> {
            String failure = null;
            PetProfile saved = null;
            try {
                AuthSession session = app.auth().requireSession();
                saved = app.myPets().save(session, id, draft);
                if (photo != null) {
                    saved = app.myPets().setPhoto(session, saved, photo);
                }
            } catch (az.miyav.common.error.AppException exception) {
                failure = exception.code();
            } catch (RuntimeException exception) {
                failure = "error.generic";
            }
            PetProfile result = saved;
            String error = failure;
            root.post(() -> {
                if (binding == null) {
                    return;
                }
                busy(false);
                if (result != null) {
                    current = result;
                    if (photo != null) {
                        app.photos().forgetPet(oldPhoto);
                    }
                    report(result.id(), false);
                }
                if (error != null) {
                    binding.messageView.setText(Codes.text(error));
                } else if (photo != null) {
                    renderPhoto();
                } else {
                    dismiss();
                }
            });
        });
    }

    private void onPicked(@Nullable Uri uri) {
        if (uri == null || binding == null) {
            return;
        }
        Context context = requireContext().getApplicationContext();
        busy(true);
        View root = binding.getRoot();
        app().io().execute(() -> {
            byte[] jpeg = null;
            try {
                jpeg = JpegEncoder.fromUri(context, uri, 1280);
            } catch (RuntimeException ignored) {
                // Handled below as an unreadable image.
            }
            byte[] encoded = jpeg;
            root.post(() -> {
                if (binding == null) {
                    return;
                }
                busy(false);
                if (encoded == null) {
                    binding.messageView.setText(R.string.error_generic);
                } else {
                    save(encoded);
                }
            });
        });
    }

    private void confirmDelete() {
        if (current == null) {
            return;
        }
        PetProfile target = current;
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.pet_delete_confirm, target.name()))
                .setPositiveButton(R.string.pet_delete, (dialog, which) -> {
                    busy(true);
                    MiyavApplication app = app();
                    View root = binding.getRoot();
                    app.io().execute(() -> {
                        String failure = null;
                        try {
                            app.myPets().delete(app.auth().requireSession(), target);
                        } catch (az.miyav.common.error.AppException exception) {
                            failure = exception.code();
                        }
                        String error = failure;
                        root.post(() -> {
                            if (binding == null) {
                                return;
                            }
                            busy(false);
                            if (error == null) {
                                report(target.id(), true);
                                dismiss();
                            } else {
                                binding.messageView.setText(Codes.text(error));
                            }
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void report(UUID petId, boolean deleted) {
        Bundle result = new Bundle();
        result.putString(PET_ID, petId.toString());
        result.putBoolean(DELETED, deleted);
        getParentFragmentManager().setFragmentResult(RESULT, result);
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.saveButton.setEnabled(!busy);
        binding.photoButton.setEnabled(!busy);
        binding.deleteButton.setEnabled(!busy);
    }
}
