package az.miyav.domain.user;

import az.miyav.common.error.ValidationException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.text.TextRules;

import java.util.Objects;
import java.util.UUID;

public final class User {
    private final UUID id;
    private final String email;
    private final String fullName;
    private final UserRole role;
    private final boolean suspended;
    private final String city;
    private final String district;
    private final String phone;
    private final LocaleCode preferredLocale;
    private final String avatarPath;
    private final String bio;
    private final boolean phonePublic;

    public User(
            UUID id,
            String email,
            String fullName,
            UserRole role,
            boolean suspended,
            String city,
            String district,
            String phone,
            LocaleCode preferredLocale
    ) {
        this(id, email, fullName, role, suspended, city, district, phone, preferredLocale, null, "", false);
    }

    public User(
            UUID id,
            String email,
            String fullName,
            UserRole role,
            boolean suspended,
            String city,
            String district,
            String phone,
            LocaleCode preferredLocale,
            String avatarPath
    ) {
        this(id, email, fullName, role, suspended, city, district, phone, preferredLocale, avatarPath, "", false);
    }

    public User(
            UUID id,
            String email,
            String fullName,
            UserRole role,
            boolean suspended,
            String city,
            String district,
            String phone,
            LocaleCode preferredLocale,
            String avatarPath,
            String bio,
            boolean phonePublic
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = email == null ? "" : email.trim();
        this.fullName = TextRules.required(fullName, 2, 100, "validation.name");
        this.role = Objects.requireNonNull(role, "role");
        this.suspended = suspended;
        this.city = TextRules.required(city, 1, 40, "validation.city");
        this.district = TextRules.optional(district, 80, "validation.profile");
        this.phone = TextRules.optional(phone, 30, "validation.profile");
        this.preferredLocale = preferredLocale == null ? LocaleCode.AZ : preferredLocale;
        this.avatarPath = avatarPath == null || avatarPath.isBlank() ? null : avatarPath;
        this.bio = TextRules.optional(bio, 200, "validation.bio");
        this.phonePublic = phonePublic;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String fullName() {
        return fullName;
    }

    public UserRole role() {
        return role;
    }

    public boolean suspended() {
        return suspended;
    }

    public String city() {
        return city;
    }

    public String district() {
        return district;
    }

    public String phone() {
        return phone;
    }

    public LocaleCode preferredLocale() {
        return preferredLocale;
    }

    public String avatarPath() {
        return avatarPath;
    }

    public String bio() {
        return bio;
    }

    public boolean phonePublic() {
        return phonePublic;
    }

    public User withLocale(LocaleCode locale) {
        return new User(id, email, fullName, role, suspended, city, district, phone, locale, avatarPath, bio, phonePublic);
    }

    public User withProfile(String fullName, String city, String district, String phone) {
        return new User(id, email, fullName, role, suspended, city, district, phone, preferredLocale, avatarPath, bio, phonePublic);
    }

    public User withProfile(String fullName, String city, String district, String phone, String bio, boolean phonePublic) {
        if (!PhoneNumbers.valid(phone)) {
            throw new ValidationException("validation.phone");
        }
        if (phonePublic && (phone == null || phone.isBlank())) {
            throw new ValidationException("validation.phone_required");
        }
        return new User(id, email, fullName, role, suspended, city, district, phone, preferredLocale, avatarPath, bio, phonePublic);
    }

    public User withAvatar(String avatarPath) {
        return new User(id, email, fullName, role, suspended, city, district, phone, preferredLocale, avatarPath, bio, phonePublic);
    }
}
