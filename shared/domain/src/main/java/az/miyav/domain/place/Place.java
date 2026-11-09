package az.miyav.domain.place;

import java.time.LocalDateTime;
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
    private final boolean open24h;
    private final String whatsapp;
    private final boolean verified;
    private final boolean active;
    private final boolean sponsored;
    private final String website;
    private final String description;

    public Place(UUID id, Kind kind, String name, String address, String phone, String openingHours,
                 double latitude, double longitude) {
        this(id, kind, name, address, phone, openingHours, latitude, longitude, false, null, false, true);
    }

    public Place(UUID id, Kind kind, String name, String address, String phone, String openingHours,
                 double latitude, double longitude, boolean open24h, String whatsapp, boolean verified, boolean active) {
        this(id, kind, name, address, phone, openingHours, latitude, longitude, open24h, whatsapp, verified, active,
                false, null, null);
    }

    public Place(UUID id, Kind kind, String name, String address, String phone, String openingHours,
                 double latitude, double longitude, boolean open24h, String whatsapp, boolean verified, boolean active,
                 boolean sponsored, String website, String description) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.address = blankToNull(address);
        this.phone = blankToNull(phone);
        this.openingHours = blankToNull(openingHours);
        this.latitude = latitude;
        this.longitude = longitude;
        this.open24h = open24h || "24/7".equals(this.openingHours);
        this.whatsapp = blankToNull(whatsapp);
        this.verified = verified;
        this.active = active;
        this.sponsored = sponsored;
        this.website = blankToNull(website);
        this.description = blankToNull(description);
    }

    /** A MIYAV partner: listed first with a large card and a large map marker. */
    public boolean sponsored() {
        return sponsored;
    }

    public Optional<String> website() {
        return Optional.ofNullable(website);
    }

    /** Short partner text shown on the sponsored card. */
    public Optional<String> description() {
        return Optional.ofNullable(description);
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

    public boolean open24h() {
        return open24h;
    }

    /** International digits without "+", for wa.me links. */
    public Optional<String> whatsapp() {
        return Optional.ofNullable(whatsapp);
    }

    /** Checked by a MIYAV admin (name, place and contacts are current). */
    public boolean verified() {
        return verified;
    }

    /** Hidden places stay in the database for admins only. */
    public boolean active() {
        return active;
    }

    /** Open at this local time (Asia/Baku); empty when the hours are unknown or not understood. */
    public Optional<Boolean> openAt(LocalDateTime local) {
        if (open24h) {
            return Optional.of(true);
        }
        return OpeningHours.openAt(openingHours, local);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
