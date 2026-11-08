package az.miyav.domain.admin;

public final class AdminStats {
    private final int users;
    private final int listings;
    private final int pendingApplications;
    private final int openReports;

    public AdminStats(int users, int listings, int pendingApplications, int openReports) {
        this.users = users;
        this.listings = listings;
        this.pendingApplications = pendingApplications;
        this.openReports = openReports;
    }

    public int users() {
        return users;
    }

    public int listings() {
        return listings;
    }

    public int pendingApplications() {
        return pendingApplications;
    }

    public int openReports() {
        return openReports;
    }
}
