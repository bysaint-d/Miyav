package az.miyav.android.ui;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import az.miyav.android.R;
import az.miyav.android.databinding.SheetPetFiltersBinding;
import az.miyav.domain.pet.AgeRange;
import az.miyav.domain.pet.PetFilters;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetSort;

import java.util.function.Consumer;

/** The detailed adoption filter: gender, age band, vaccinated only and order, applied together. */
final class PetFiltersSheet {
    private static final String GENDER = "filters_gender";
    private static final String AGE = "filters_age";
    private static final String VACCINATED = "filters_vaccinated";
    private static final String SORT = "filters_sort";

    private PetFiltersSheet() {
    }

    static void show(Fragment host, PetFilters current, boolean nearby, Consumer<PetFilters> onApply) {
        BottomSheetDialog sheet = new BottomSheetDialog(host.requireContext());
        SheetPetFiltersBinding view = SheetPetFiltersBinding.inflate(host.getLayoutInflater());
        render(view, current);
        view.nearbyNote.setVisibility(nearby ? View.VISIBLE : View.GONE);
        view.sortGroup.setEnabled(!nearby);
        for (int i = 0; i < view.sortGroup.getChildCount(); i++) {
            view.sortGroup.getChildAt(i).setEnabled(!nearby);
        }
        view.resetButton.setOnClickListener(button -> render(view, PetFilters.NONE));
        view.applyButton.setOnClickListener(button -> {
            sheet.dismiss();
            onApply.accept(read(view));
        });
        sheet.setContentView(view.getRoot());
        sheet.show();
    }

    static void save(Bundle state, PetFilters filters) {
        state.putString(GENDER, filters.gender() == null ? "" : filters.gender().name());
        state.putString(AGE, filters.age().name());
        state.putBoolean(VACCINATED, filters.vaccinatedOnly());
        state.putString(SORT, filters.sort().name());
    }

    static PetFilters restore(Bundle state) {
        if (state == null || !state.containsKey(AGE)) {
            return PetFilters.NONE;
        }
        try {
            String gender = state.getString(GENDER, "");
            return new PetFilters(gender.isEmpty() ? null : PetGender.valueOf(gender),
                    AgeRange.valueOf(state.getString(AGE, AgeRange.ANY.name())),
                    state.getBoolean(VACCINATED, false),
                    PetSort.valueOf(state.getString(SORT, PetSort.NEWEST.name())));
        } catch (IllegalArgumentException exception) {
            return PetFilters.NONE;
        }
    }

    private static void render(SheetPetFiltersBinding view, PetFilters filters) {
        view.genderGroup.check(filters.gender() == PetGender.MALE ? R.id.genderMale
                : filters.gender() == PetGender.FEMALE ? R.id.genderFemale : R.id.genderAny);
        view.ageGroup.check(switch (filters.age()) {
            case BABY -> R.id.ageBaby;
            case YOUNG -> R.id.ageYoung;
            case ADULT -> R.id.ageAdult;
            case SENIOR -> R.id.ageSenior;
            default -> R.id.ageAny;
        });
        view.vaccinatedSwitch.setChecked(filters.vaccinatedOnly());
        view.sortGroup.check(switch (filters.sort()) {
            case OLDEST -> R.id.sortOldest;
            case YOUNGEST -> R.id.sortYoungest;
            case ELDEST -> R.id.sortEldest;
            default -> R.id.sortNewest;
        });
    }

    private static PetFilters read(SheetPetFiltersBinding view) {
        int gender = view.genderGroup.getCheckedChipId();
        int age = view.ageGroup.getCheckedChipId();
        int sort = view.sortGroup.getCheckedChipId();
        return new PetFilters(
                gender == R.id.genderMale ? PetGender.MALE : gender == R.id.genderFemale ? PetGender.FEMALE : null,
                age == R.id.ageBaby ? AgeRange.BABY : age == R.id.ageYoung ? AgeRange.YOUNG
                        : age == R.id.ageAdult ? AgeRange.ADULT : age == R.id.ageSenior ? AgeRange.SENIOR : AgeRange.ANY,
                view.vaccinatedSwitch.isChecked(),
                sort == R.id.sortOldest ? PetSort.OLDEST : sort == R.id.sortYoungest ? PetSort.YOUNGEST
                        : sort == R.id.sortEldest ? PetSort.ELDEST : PetSort.NEWEST);
    }
}
