package az.miyav.domain.admin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One line of a recent-activity feed: a registration, a walk or an adoption listing change. */
public final class ActivityItem {
    public enum Kind { REGISTRATION, WALK, ADOPTION }

    private final Kind kind;
    private final UUID id;
    private final String title;
    private final String detail;
    private final String status;
    private final Instant at;

    public ActivityItem(Kind kind, UUID id, String title, String detail, String status, Instant at) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.id = Objects.requireNonNull(id, "id");
        this.title = title == null ? "" : title;
        this.detail = detail == null ? "" : detail;
        this.status = status == null ? "" : status;
        this.at = at;
    }

    public Kind kind() {
        return kind;
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public String status() {
        return status;
    }

    public Instant at() {
        return at;
    }
}
