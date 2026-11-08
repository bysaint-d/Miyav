package az.miyav.domain.admin;

import az.miyav.domain.user.UserRole;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** A community member as moderators see them: public profile fields plus activity counts. */
public final class UserSummary {
    public enum Activity { NEW, ACTIVE, QUIET }

    private final UUID id;
    private final String fullName;
    private final String city;
    private final String district;
    private final UserRole role;
    private final boolean suspended;
    private final String avatarPath;
    private final Instant joinedAt;
    private final int pets;
    private final int listings;
    private final int walks;
    private final int requests;
    private final Instant lastActiveAt;

    public UserSummary(UUID id, String fullName, String city, String district, UserRole role, boolean suspended,
                       String avatarPath, Instant joinedAt, int pets, int listings, int walks, int requests,
                       Instant lastActiveAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.fullName = fullName == null ? "" : fullName;
        this.city = city == null ? "" : city;
        this.district = district == null ? "" : district;
        this.role = Objects.requireNonNull(role, "role");
        this.suspended = suspended;
        this.avatarPath = avatarPath == null || avatarPath.isBlank() ? null : avatarPath;
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
        this.pets = pets;
        this.listings = listings;
        this.walks = walks;
        this.requests = requests;
        this.lastActiveAt = lastActiveAt == null ? joinedAt : lastActiveAt;
    }

    public UUID id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public String city() {
        return city;
    }

    public String district() {
        return district;
    }

    public UserRole role() {
        return role;
    }

    public boolean suspended() {
        return suspended;
    }

    public String avatarPath() {
        return avatarPath;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public int pets() {
        return pets;
    }

    public int listings() {
        return listings;
    }

    public int walks() {
        return walks;
    }

    public int requests() {
        return requests;
    }

    public Instant lastActiveAt() {
        return lastActiveAt;
    }

    public boolean walkHelper() {
        return role == UserRole.SITTER;
    }

    /** Joined in the last week, active in the last month, or quiet. */
    public Activity activity(Instant now) {
        if (Duration.between(joinedAt, now).toDays() < 7) {
            return Activity.NEW;
        }
        return Duration.between(lastActiveAt, now).toDays() <= 30 ? Activity.ACTIVE : Activity.QUIET;
    }

    public String initials() {
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            }
        }
        return initials.length() == 0 ? "?" : initials.toString();
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return fullName.toLowerCase(Locale.ROOT).contains(needle)
                || city.toLowerCase(Locale.ROOT).contains(needle)
                || district.toLowerCase(Locale.ROOT).contains(needle);
    }
}
