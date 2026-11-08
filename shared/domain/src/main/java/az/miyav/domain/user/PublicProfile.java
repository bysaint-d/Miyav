package az.miyav.domain.user;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** What another signed-in user may see about a publisher or walker. */
public final class PublicProfile {
    private final UUID id;
    private final String fullName;
    private final String avatarPath;
    private final String city;
    private final String bio;
    private final boolean verified;
    private final String contactPhone;
    private final Instant memberSince;

    public PublicProfile(UUID id, String fullName, String avatarPath, String city, String bio,
                         boolean verified, String contactPhone, Instant memberSince) {
        this.id = Objects.requireNonNull(id, "id");
        this.fullName = fullName == null ? "" : fullName;
        this.avatarPath = avatarPath == null || avatarPath.isBlank() ? null : avatarPath;
        this.city = city == null ? "" : city;
        this.bio = bio == null ? "" : bio;
        this.verified = verified;
        this.contactPhone = contactPhone == null || contactPhone.isBlank() ? null : contactPhone;
        this.memberSince = memberSince;
    }

    public UUID id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public String avatarPath() {
        return avatarPath;
    }

    public String city() {
        return city;
    }

    public String bio() {
        return bio;
    }

    public boolean verified() {
        return verified;
    }

    /** International digits for tel: / wa.me, present only when the owner chose to show the number. */
    public Optional<String> contactDigits() {
        return PhoneNumbers.internationalDigits(contactPhone);
    }

    public Instant memberSince() {
        return memberSince;
    }
}
