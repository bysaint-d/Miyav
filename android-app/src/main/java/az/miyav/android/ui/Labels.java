package az.miyav.android.ui;

import android.content.Context;

import az.miyav.android.R;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.pet.Vaccination;
import az.miyav.domain.user.UserRole;

import java.text.DateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

final class Labels {
    static final List<String> CITIES = List.of("Bakı", "Sumqayıt", "Abşeron");

    private Labels() {
    }

    static String species(Context context, PetSpecies species) {
        if (species == null) {
            return context.getString(R.string.filter_all);
        }
        return context.getString(switch (species) {
            case DOG -> R.string.species_dog;
            case CAT -> R.string.species_cat;
            case BIRD -> R.string.species_bird;
            case OTHER -> R.string.species_other;
        });
    }

    static String gender(Context context, PetGender gender) {
        return context.getString(switch (gender) {
            case FEMALE -> R.string.gender_female;
            case MALE -> R.string.gender_male;
            case UNKNOWN -> R.string.gender_unknown;
        });
    }

    static String vaccination(Context context, Vaccination vaccination) {
        return context.getString(switch (vaccination) {
            case YES -> R.string.vaccination_yes;
            case NO -> R.string.vaccination_no;
            case UNKNOWN -> R.string.vaccination_unknown;
        });
    }

    static String speciesEmoji(PetSpecies species) {
        if (species == null) {
            return "🐾";
        }
        return switch (species) {
            case DOG -> "🐶";
            case CAT -> "🐱";
            case BIRD -> "🐦";
            case OTHER -> "🐾";
        };
    }

    /** Pastel fill per species, used for badges and map markers. */
    static int speciesColor(Context context, PetSpecies species) {
        int color = species == null ? R.color.pastel_butter : switch (species) {
            case DOG -> R.color.pastel_peach;
            case CAT -> R.color.pastel_lavender;
            case BIRD -> R.color.pastel_sky;
            case OTHER -> R.color.pastel_mint;
        };
        return androidx.core.content.ContextCompat.getColor(context, color);
    }

    static String distance(Context context, double meters) {
        if (meters < 1000) {
            int rounded = Math.max(100, (int) (Math.round(meters / 100.0) * 100));
            return context.getString(R.string.distance_m, rounded);
        }
        double km = meters / 1000.0;
        String value = km < 10
                ? String.format(java.util.Locale.getDefault(), "%.1f", km)
                : String.valueOf(Math.round(km));
        return context.getString(R.string.distance_km, value);
    }

    static String status(Context context, PetStatus status) {
        return context.getString(switch (status) {
            case DRAFT -> R.string.pet_status_draft;
            case PUBLISHED -> R.string.pet_status_published;
            case ADOPTED -> R.string.pet_status_adopted;
            case ARCHIVED -> R.string.pet_status_archived;
            case PENDING -> R.string.pet_status_pending;
            case REJECTED -> R.string.pet_status_rejected;
        });
    }

    static String status(Context context, ApplicationStatus status) {
        return context.getString(switch (status) {
            case PENDING -> R.string.adoption_status_pending;
            case ACCEPTED -> R.string.adoption_status_accepted;
            case REJECTED -> R.string.adoption_status_rejected;
            case CANCELLED -> R.string.adoption_status_cancelled;
        });
    }

    static String role(Context context, UserRole role) {
        return context.getString(switch (role) {
            case OWNER -> R.string.role_owner;
            case SITTER -> R.string.role_sitter;
            case ADMIN -> R.string.role_admin;
        });
    }

    static String age(Context context, int months) {
        int years = months / 12;
        int rest = months % 12;
        if (years == 0) {
            return context.getString(R.string.age_months, rest);
        }
        if (rest == 0) {
            return context.getString(R.string.age_years, years);
        }
        return context.getString(R.string.age_years_months, years, rest);
    }

    static String care(Context context, Pet pet) {
        return context.getString(switch (pet.species()) {
            case DOG -> R.string.pet_care_dog;
            case CAT -> R.string.pet_care_cat;
            case BIRD -> R.string.pet_care_bird;
            case OTHER -> R.string.pet_care_other;
        });
    }

    static String summary(Context context, Pet pet) {
        String place = pet.location() != null && !pet.location().name().isEmpty() ? pet.location().name() : pet.city();
        String breed = pet.breed().isEmpty() ? species(context, pet.species()) : pet.breed();
        return place + "  ·  " + breed + "  ·  " + age(context, pet.ageMonths());
    }

    static String place(Pet pet) {
        if (pet.location() != null && !pet.location().name().isEmpty()) {
            return pet.location().name().contains(pet.city()) ? pet.location().name() : pet.location().name() + ", " + pet.city();
        }
        return pet.city();
    }

    static String date(Instant instant) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(instant));
    }

    static List<PetSpecies> speciesWithAll() {
        return Arrays.asList(null, PetSpecies.DOG, PetSpecies.CAT, PetSpecies.BIRD, PetSpecies.OTHER);
    }

    static List<String> citiesWithAll() {
        return Arrays.asList(null, "Bakı", "Sumqayıt", "Abşeron");
    }
}
