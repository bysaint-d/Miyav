package az.miyav.android.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentWalkEditorBinding;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.walk.WalkListing;
import az.miyav.domain.walk.WalkListingDetails;
import az.miyav.domain.walk.WalkListingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/** Owner's walk request: pet, when, how long, price per hour (the total is shown live) and an approximate area. */
public class WalkEditorFragment extends Fragment {
    private FragmentWalkEditorBinding binding;
    private Choice<PetSpecies> species;
    private Choice<Integer> duration;
    private Choice<String> city;
    private WalkListing current;
    private ListingLocation area;
    private LocalDate date;
    private LocalTime time;
    private Form snapshot;
    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWalkEditorBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        species = new Choice<>(binding.speciesInput, Arrays.asList(PetSpecies.values()),
                value -> Labels.speciesEmoji(value) + "  " + Labels.species(requireContext(), value), PetSpecies.DOG);
        duration = new Choice<>(binding.durationInput, Labels.walkDurations(),
                value -> Labels.duration(requireContext(), value), 60);
        duration.onChange(this::renderTotal);
        city = new Choice<>(binding.cityInput, Labels.CITIES, value -> value, Labels.CITIES.get(0));
        if (date == null) {
            LocalDateTime start = LocalDateTime.now().plusHours(2).truncatedTo(ChronoUnit.HOURS);
            date = start.toLocalDate();
            time = start.toLocalTime();
        }
        binding.priceInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.priceLayout.setError(null);
                renderTotal();
            }
        });
        binding.dateButton.setOnClickListener(view -> pickDate());
        binding.timeButton.setOnClickListener(view -> pickTime());
        binding.locationButton.setOnClickListener(view -> openPicker());
        binding.publishButton.setOnClickListener(view -> save());
        getParentFragmentManager().setFragmentResultListener(LocationPickerFragment.RESULT, getViewLifecycleOwner(), (key, result) -> {
            area = new ListingLocation(result.getDouble(LocationPickerFragment.LATITUDE),
                    result.getDouble(LocationPickerFragment.LONGITUDE), result.getString(LocationPickerFragment.NAME));
            renderArea();
        });
        if (snapshot != null) {
            fill(snapshot);
        } else {
            String listingId = requireArguments().getString("listingId", "");
            if (listingId != null && !listingId.isBlank() && !loaded) {
                load(UUID.fromString(listingId));
            }
        }
        render();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        snapshot = form();
        binding = null;
        super.onDestroyView();
    }

    private void load(UUID listingId) {
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.walkListings().find(app.auth().requireSession(), listingId).orElse(null);
        }, listing -> {
            busy(false);
            loaded = true;
            if (listing == null || listing.status() != WalkListingStatus.OPEN) {
                binding.messageView.setText(R.string.walk_listing_closed);
                binding.publishButton.setEnabled(false);
                return;
            }
            current = listing;
            WalkListingDetails details = listing.details();
            area = details.area();
            LocalDateTime start = LocalDateTime.ofInstant(details.startsAt(), ZoneId.systemDefault());
            date = start.toLocalDate();
            time = start.toLocalTime();
            fill(new Form(details.petName(), details.species(), details.durationMinutes(),
                    Labels.money(details.pricePerHour()), details.city(), details.notes()));
            render();
        }, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void fill(Form form) {
        binding.nameInput.setText(form.petName);
        species.select(form.species);
        duration.select(form.minutes);
        binding.priceInput.setText(form.priceText);
        city.select(form.city);
        binding.notesInput.setText(form.notes);
    }

    private Form form() {
        return new Form(Ui.text(binding.nameInput).trim(), species.value(), duration.value(),
                Ui.text(binding.priceInput).trim(), city.value(), Ui.text(binding.notesInput).trim());
    }

    private void render() {
        binding.toolbar.setTitle(current == null ? R.string.wl_editor_title : R.string.wl_editor_edit);
        binding.publishButton.setText(current == null ? R.string.wl_publish : R.string.profile_save);
        renderWhen();
        renderTotal();
        renderArea();
    }

    private void renderWhen() {
        binding.dateButton.setText(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                .format(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())));
        binding.timeButton.setText(android.text.format.DateFormat.getTimeFormat(requireContext())
                .format(Date.from(startsAt())));
    }

    private void renderTotal() {
        if (binding == null) {
            return;
        }
        BigDecimal price = price(Ui.text(binding.priceInput));
        Integer minutes = duration.value();
        if (price == null || minutes == null || price.signum() <= 0) {
            binding.totalView.setText("");
            return;
        }
        BigDecimal total = price.multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        binding.totalView.setText(getString(R.string.wl_total, Labels.money(total), Labels.duration(requireContext(), minutes)));
    }

    private void renderArea() {
        if (binding == null) {
            return;
        }
        if (area == null) {
            binding.locationValue.setText(R.string.pet_location_none);
            binding.locationButton.setText(R.string.pet_location_choose);
        } else {
            binding.locationValue.setText(area.name().isEmpty()
                    ? String.format(java.util.Locale.US, "%.2f, %.2f", area.latitude(), area.longitude())
                    : area.name());
            binding.locationButton.setText(R.string.pet_location_change);
        }
    }

    private Instant startsAt() {
        return LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant();
    }

    private void pickDate() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.wl_date)
                .setSelection(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.now())
                        .build())
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            date = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate();
            binding.timeError.setVisibility(View.GONE);
            renderWhen();
        });
        picker.show(getChildFragmentManager(), "walk-date");
    }

    private void pickTime() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(android.text.format.DateFormat.is24HourFormat(requireContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(time.getHour())
                .setMinute(time.getMinute())
                .setTitleText(R.string.wl_time)
                .build();
        picker.addOnPositiveButtonClickListener(view -> {
            time = LocalTime.of(picker.getHour(), picker.getMinute());
            binding.timeError.setVisibility(View.GONE);
            renderWhen();
        });
        picker.show(getChildFragmentManager(), "walk-time");
    }

    private void openPicker() {
        Bundle args = new Bundle();
        if (area != null) {
            args.putDouble(LocationPickerFragment.LATITUDE, area.latitude());
            args.putDouble(LocationPickerFragment.LONGITUDE, area.longitude());
        }
        Ui.nav(this).navigate(R.id.action_global_location_picker, args);
    }

    private static BigDecimal price(String text) {
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Builds the request and shows the first problem next to its field; null when something is invalid. */
    private WalkListingDetails details() {
        binding.nameLayout.setError(null);
        binding.priceLayout.setError(null);
        binding.notesLayout.setError(null);
        binding.timeError.setVisibility(View.GONE);
        Form form = form();
        BigDecimal price = price(form.priceText);
        if (price == null) {
            binding.priceLayout.setError(getString(R.string.validation_walk_price));
            return null;
        }
        Instant start = startsAt();
        if (start.isBefore(Instant.now()) || start.isAfter(Instant.now().plus(60, ChronoUnit.DAYS))) {
            binding.timeError.setVisibility(View.VISIBLE);
            return null;
        }
        try {
            return new WalkListingDetails(form.petName, form.species, start, form.minutes == null ? 0 : form.minutes,
                    price, form.city, area, form.notes);
        } catch (ValidationException exception) {
            switch (exception.code()) {
                case "validation.walk_pet" -> binding.nameLayout.setError(getString(R.string.validation_walk_pet));
                case "validation.walk_price" -> binding.priceLayout.setError(getString(R.string.validation_walk_price));
                case "validation.walk_notes" -> binding.notesLayout.setError(getString(R.string.validation_walk_notes));
                default -> binding.messageView.setText(Codes.text(exception.code()));
            }
            return null;
        }
    }

    private void save() {
        WalkListingDetails details = details();
        if (details == null) {
            return;
        }
        WalkListing editing = current;
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.auth().requireSession();
            return editing == null
                    ? app.walkListings().publish(session, details)
                    : app.walkListings().update(session, editing, details);
        }, saved -> {
            busy(false);
            Ui.snack(binding.getRoot(), R.string.wl_published);
            Ui.nav(this).popBackStack();
            if (editing == null) {
                Bundle args = new Bundle();
                args.putString("listingId", saved.id().toString());
                Ui.nav(this).navigate(R.id.action_global_walk_detail, args);
            }
        }, code -> {
            busy(false);
            if ("validation.walk_time".equals(code)) {
                binding.timeError.setVisibility(View.VISIBLE);
            }
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.publishButton.setEnabled(!busy);
        binding.locationButton.setEnabled(!busy);
    }

    private static final class Form {
        final String petName;
        final PetSpecies species;
        final Integer minutes;
        final String priceText;
        final String city;
        final String notes;

        Form(String petName, PetSpecies species, Integer minutes, String priceText, String city, String notes) {
            this.petName = petName;
            this.species = species;
            this.minutes = minutes;
            this.priceText = priceText;
            this.city = city;
            this.notes = notes;
        }
    }
}
