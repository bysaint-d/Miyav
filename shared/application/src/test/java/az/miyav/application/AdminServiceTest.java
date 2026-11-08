package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.AdminRepository;
import az.miyav.domain.repository.AdoptionRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import az.miyav.security.RoleGuard;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTest {
    @Test
    void ownerCannotCallAdminActions() {
        UserRepository users = mock(UserRepository.class);
        AdminRepository admin = mock(AdminRepository.class);
        AdminService service = new AdminService(users, mock(PetRepository.class), mock(AdoptionRepository.class), admin, new RoleGuard());
        UUID actor = UUID.randomUUID();
        AuthSession session = new AuthSession(actor, "a@example.com", "token", "refresh", Instant.parse("2026-11-08T01:00:00Z"), UserRole.OWNER);
        assertThrows(AccessDeniedException.class, () -> service.users(session));
        verify(users, never()).listDirectory(session);
    }

    @Test
    void adminReadsTheDirectory() {
        UserRepository users = mock(UserRepository.class);
        AdminService service = new AdminService(users, mock(PetRepository.class), mock(AdoptionRepository.class), mock(AdminRepository.class), new RoleGuard());
        UUID actor = UUID.randomUUID();
        AuthSession session = new AuthSession(actor, "a@example.com", "token", "refresh", Instant.parse("2026-11-08T01:00:00Z"), UserRole.ADMIN);
        when(users.listDirectory(session)).thenReturn(List.of(new User(actor, "a@example.com", "Aysel Məmmədova", UserRole.ADMIN, false, "Bakı", "", "", LocaleCode.AZ)));
        service.users(session);
        verify(users).listDirectory(session);
    }
}
