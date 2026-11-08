package az.miyav.application;

import az.miyav.domain.admin.AdminStats;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.moderation.Report;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.repository.AdminRepository;
import az.miyav.domain.repository.AdoptionRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.User;
import az.miyav.domain.walk.WalkRequest;
import az.miyav.domain.walk.WalkRequestStatus;
import az.miyav.common.error.ValidationException;
import az.miyav.security.RoleGuard;

import java.util.List;
import java.util.UUID;

public final class AdminService {
    private final UserRepository users;
    private final PetRepository pets;
    private final AdoptionRepository adoptions;
    private final AdminRepository admin;
    private final RoleGuard access;

    public AdminService(
            UserRepository users,
            PetRepository pets,
            AdoptionRepository adoptions,
            AdminRepository admin,
            RoleGuard access
    ) {
        this.users = users;
        this.pets = pets;
        this.adoptions = adoptions;
        this.admin = admin;
        this.access = access;
    }

    public AdminStats stats(AuthSession session) {
        requireAdmin(session);
        List<User> directory = users.listDirectory(session);
        List<Pet> listings = pets.findForAdmin(session);
        List<AdoptionApplication> applications = adoptions.findForAdmin(session);
        List<Report> reports = admin.listReports(session);
        long pending = applications.stream().filter(application -> application.status() == ApplicationStatus.PENDING).count();
        long openReports = reports.stream().filter(report -> !report.resolved()).count();
        return new AdminStats(directory.size(), listings.size(), (int) pending, (int) openReports);
    }

    public List<User> users(AuthSession session) {
        requireAdmin(session);
        return users.listDirectory(session);
    }

    public List<Pet> listings(AuthSession session) {
        requireAdmin(session);
        return pets.findForAdmin(session);
    }

    public List<AdoptionApplication> applications(AuthSession session) {
        requireAdmin(session);
        return adoptions.findForAdmin(session);
    }

    public List<Report> reports(AuthSession session) {
        requireAdmin(session);
        return admin.listReports(session);
    }

    public void suspend(AuthSession session, UUID userId, boolean suspended, String note) {
        requireAdmin(session);
        admin.execute(session, "suspend", userId, Boolean.toString(suspended), note == null ? "" : note);
    }

    public void archiveListing(AuthSession session, UUID listingId, String note) {
        requireAdmin(session);
        admin.execute(session, "listing", listingId, "deleted", note == null ? "" : note);
    }

    public void restoreListing(AuthSession session, UUID listingId, String note) {
        requireAdmin(session);
        admin.execute(session, "listing", listingId, "active", note == null ? "" : note);
    }

    /** Cancels a walking request that has not finished; both people are notified by the server. */
    public void cancelWalkRequest(AuthSession session, WalkRequest request, java.time.Instant now, String note) {
        requireAdmin(session);
        if (!request.status(now).cancellableByModerator()) {
            throw new ValidationException("walk.request_closed");
        }
        admin.execute(session, "booking", request.id(), WalkRequestStatus.CANCELLED.databaseValue(), note == null ? "" : note);
    }

    public void resolveReport(AuthSession session, UUID reportId, String note) {
        requireAdmin(session);
        admin.execute(session, "report", reportId, "true", note == null ? "" : note);
    }

    public boolean canModerate(Pet listing) {
        return listing.status() != PetStatus.ADOPTED;
    }

    private void requireAdmin(AuthSession session) {
        if (session == null) {
            access.requireAdmin(null);
            return;
        }
        access.requireAdmin(session.role());
    }
}
