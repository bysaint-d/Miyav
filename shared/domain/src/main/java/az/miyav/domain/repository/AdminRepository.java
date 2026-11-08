package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.moderation.Report;

import java.util.List;
import java.util.UUID;

public interface AdminRepository {
    void execute(AuthSession session, String action, UUID targetId, String value, String note);

    List<Report> listReports(AuthSession session);
}
