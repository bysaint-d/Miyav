package az.miyav.domain.admin;

import java.util.Objects;

/** How much is happening in one city: members, open adoption listings and walk helpers. */
public final class CityActivity {
    private final String city;
    private final int users;
    private final int listings;
    private final int helpers;

    public CityActivity(String city, int users, int listings, int helpers) {
        this.city = Objects.requireNonNull(city, "city");
        this.users = users;
        this.listings = listings;
        this.helpers = helpers;
    }

    public String city() {
        return city;
    }

    public int users() {
        return users;
    }

    public int listings() {
        return listings;
    }

    public int helpers() {
        return helpers;
    }
}
