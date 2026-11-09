package az.miyav.domain.walk;

import az.miyav.common.error.ValidationException;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetSpecies;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What an owner fills in when asking for a walk: the pet, when, for how long, the hourly price and
 * the area. Validated on construction, so an invalid listing can never reach the server.
 */
public final class WalkListingDetails {
    public static final int MIN_MINUTES = 15;
    public static final int MAX_MINUTES = 480;
    public static final BigDecimal MIN_PRICE = BigDecimal.ONE;
    public static final BigDecimal MAX_PRICE = new BigDecimal("500");

    private final String petName;
    private final PetSpecies species;
    private final Instant startsAt;
    private final int durationMinutes;
    private final BigDecimal pricePerHour;
    private final String city;
    private final ListingLocation area;
    private final String notes;
    private final UUID petId;

    public WalkListingDetails(String petName, PetSpecies species, Instant startsAt, int durationMinutes,
                              BigDecimal pricePerHour, String city, ListingLocation area, String notes) {
        this(petName, species, startsAt, durationMinutes, pricePerHour, city, area, notes, null);
    }

    /** {@code petId} links one of the owner's own pets, whose photo the listing then shows; may be null. */
    public WalkListingDetails(String petName, PetSpecies species, Instant startsAt, int durationMinutes,
                              BigDecimal pricePerHour, String city, ListingLocation area, String notes, UUID petId) {
        String name = petName == null ? "" : petName.trim();
        if (name.isEmpty() || name.length() > 60) {
            throw new ValidationException("validation.walk_pet");
        }
        if (durationMinutes < MIN_MINUTES || durationMinutes > MAX_MINUTES) {
            throw new ValidationException("validation.walk_duration");
        }
        if (pricePerHour == null || pricePerHour.compareTo(MIN_PRICE) < 0 || pricePerHour.compareTo(MAX_PRICE) > 0) {
            throw new ValidationException("validation.walk_price");
        }
        if (city == null || !Pet.SUPPORTED_CITIES.contains(city)) {
            throw new ValidationException("validation.city");
        }
        String note = notes == null ? "" : notes.trim();
        if (note.length() > 500) {
            throw new ValidationException("validation.walk_notes");
        }
        this.petName = name;
        this.species = Objects.requireNonNull(species, "species");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.durationMinutes = durationMinutes;
        this.pricePerHour = pricePerHour.setScale(2, RoundingMode.HALF_UP);
        this.city = city;
        this.area = area == null ? null : approximate(area);
        this.notes = note;
        this.petId = petId;
    }

    public Optional<UUID> petId() {
        return Optional.ofNullable(petId);
    }

    public String petName() {
        return petName;
    }

    public PetSpecies species() {
        return species;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public int durationMinutes() {
        return durationMinutes;
    }

    public Instant endsAt() {
        return startsAt.plusSeconds(durationMinutes * 60L);
    }

    public BigDecimal pricePerHour() {
        return pricePerHour;
    }

    /** Hourly price times the duration, e.g. 10 ₼/hour for 90 minutes is 15 ₼. */
    public BigDecimal totalPrice() {
        return total(pricePerHour, durationMinutes);
    }

    /** Price for a walk of this length at an hourly rate, rounded to qəpik. */
    public static BigDecimal total(BigDecimal pricePerHour, int minutes) {
        return pricePerHour.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** Checks an hourly price a walker proposes; the same range as the owner's price. */
    public static BigDecimal requireValidPrice(BigDecimal price) {
        if (price == null || price.compareTo(MIN_PRICE) < 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new ValidationException("validation.walk_price");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    public String city() {
        return city;
    }

    /** About 1 km precision; the exact meeting point is agreed privately. */
    public ListingLocation area() {
        return area;
    }

    public String notes() {
        return notes;
    }

    /** A new listing must start soon enough to be useful and not in the past. */
    public void requireBookable(Instant now) {
        if (startsAt.isBefore(now.minusSeconds(300)) || startsAt.isAfter(now.plusSeconds(60L * 24 * 3600))) {
            throw new ValidationException("validation.walk_time");
        }
    }

    private static ListingLocation approximate(ListingLocation location) {
        return new ListingLocation(Math.round(location.latitude() * 100) / 100.0,
                Math.round(location.longitude() * 100) / 100.0, location.name());
    }
}
