package az.miyav.domain.admin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the whole MIYAV community for the admin overview. Built from live database counts;
 * collections are copied and read-only so the snapshot cannot change after it is created.
 */
public final class PlatformOverview {
    private final Counts counts;
    private final Map<String, Integer> requestsByStatus;
    private final Map<String, Integer> listingsByStatus;
    private final List<DailyCount> registrations;
    private final List<DailyCount> walks;
    private final List<CityActivity> cities;
    private final List<ActivityItem> recentUsers;
    private final List<ActivityItem> recentWalks;
    private final List<ActivityItem> recentListings;

    public PlatformOverview(Counts counts, Map<String, Integer> requestsByStatus, Map<String, Integer> listingsByStatus,
                            List<DailyCount> registrations, List<DailyCount> walks, List<CityActivity> cities,
                            List<ActivityItem> recentUsers, List<ActivityItem> recentWalks,
                            List<ActivityItem> recentListings) {
        this.counts = counts;
        this.requestsByStatus = Collections.unmodifiableMap(new LinkedHashMap<>(requestsByStatus));
        this.listingsByStatus = Collections.unmodifiableMap(new LinkedHashMap<>(listingsByStatus));
        this.registrations = List.copyOf(registrations);
        this.walks = List.copyOf(walks);
        this.cities = List.copyOf(cities);
        this.recentUsers = List.copyOf(recentUsers);
        this.recentWalks = List.copyOf(recentWalks);
        this.recentListings = List.copyOf(recentListings);
    }

    public Counts counts() {
        return counts;
    }

    public Map<String, Integer> requestsByStatus() {
        return requestsByStatus;
    }

    public Map<String, Integer> listingsByStatus() {
        return listingsByStatus;
    }

    public int walkingRequests() {
        return requestsByStatus.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int activeListings() {
        return listingsByStatus.getOrDefault("active", 0);
    }

    public int completedAdoptions() {
        return listingsByStatus.getOrDefault("adopted", 0);
    }

    public List<DailyCount> registrations() {
        return registrations;
    }

    public List<DailyCount> walks() {
        return walks;
    }

    public List<CityActivity> cities() {
        return cities;
    }

    public List<ActivityItem> recentUsers() {
        return recentUsers;
    }

    public List<ActivityItem> recentWalks() {
        return recentWalks;
    }

    public List<ActivityItem> recentListings() {
        return recentListings;
    }

    /** Plain community totals. */
    public static final class Counts {
        private final int users;
        private final int suspendedUsers;
        private final int newUsersThisWeek;
        private final int activeUsers;
        private final int pets;
        private final int activeHelpers;
        private final int verifiedHelpers;
        private final int upcomingRequests;
        private final int walks;
        private final int liveWalks;
        private final int pendingApplications;
        private final int openReports;
        private final int conversations;
        private final int messagesThisWeek;
        private final int notifications;
        private final int unreadNotifications;

        public Counts(int users, int suspendedUsers, int newUsersThisWeek, int activeUsers, int pets,
                      int activeHelpers, int verifiedHelpers, int upcomingRequests, int walks, int liveWalks,
                      int pendingApplications, int openReports, int conversations, int messagesThisWeek,
                      int notifications, int unreadNotifications) {
            this.users = users;
            this.suspendedUsers = suspendedUsers;
            this.newUsersThisWeek = newUsersThisWeek;
            this.activeUsers = activeUsers;
            this.pets = pets;
            this.activeHelpers = activeHelpers;
            this.verifiedHelpers = verifiedHelpers;
            this.upcomingRequests = upcomingRequests;
            this.walks = walks;
            this.liveWalks = liveWalks;
            this.pendingApplications = pendingApplications;
            this.openReports = openReports;
            this.conversations = conversations;
            this.messagesThisWeek = messagesThisWeek;
            this.notifications = notifications;
            this.unreadNotifications = unreadNotifications;
        }

        public int users() {
            return users;
        }

        public int suspendedUsers() {
            return suspendedUsers;
        }

        public int newUsersThisWeek() {
            return newUsersThisWeek;
        }

        public int activeUsers() {
            return activeUsers;
        }

        public int pets() {
            return pets;
        }

        public int activeHelpers() {
            return activeHelpers;
        }

        public int verifiedHelpers() {
            return verifiedHelpers;
        }

        public int upcomingRequests() {
            return upcomingRequests;
        }

        public int walks() {
            return walks;
        }

        public int liveWalks() {
            return liveWalks;
        }

        public int pendingApplications() {
            return pendingApplications;
        }

        public int openReports() {
            return openReports;
        }

        public int conversations() {
            return conversations;
        }

        public int messagesThisWeek() {
            return messagesThisWeek;
        }

        public int notifications() {
            return notifications;
        }

        public int unreadNotifications() {
            return unreadNotifications;
        }
    }
}
