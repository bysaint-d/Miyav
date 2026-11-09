package az.miyav.domain.place;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A vet clinic or a shop selling pet food and supplies, shown on the Vets tab. */
public final class Place {
    public enum Kind {
        VET("vet"), PET_SHOP("pet_shop");

        private final String databaseValue;

        Kind(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static Kind fromDatabase(String value) {
            for (Kind kind : values()) {
                if (kind.databaseValue.equals(value)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown place kind: " + value);
        }
    }

    private final UUID id;
    private final Kind kind;
    private final String name;
    private final String address;
    private final String phone;
    private final String openingHours;
    private final double latitude;
    private final double longitude;

    public Place(UUID id, Kind kind, String name, String address, String phone, String openingHours,
                 double latitude, double longitude) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.address = blankToNull(address);
        this.phone = blankToNull(phone);
        this.openingHours = blankToNull(openingHours);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UUID id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    public Optional<String> address() {
        return Optional.ofNullable(address);
    }

    public Optional<String> phone() {
        return Optional.ofNullable(phone);
    }

    public Optional<String> openingHours() {
        return Optional.ofNullable(openingHours);
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
