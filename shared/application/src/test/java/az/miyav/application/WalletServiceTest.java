package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.WalletRepository;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.wallet.Wallet;
import az.miyav.domain.wallet.Withdrawal;
import az.miyav.security.RoleGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WalletServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private final WalletRepository repository = mock(WalletRepository.class);
    private final WalletService service = new WalletService(repository, new RoleGuard());
    private final AuthSession walker = session(UserRole.OWNER);
    private final AuthSession admin = session(UserRole.ADMIN);

    @Test
    void theFeeIsTenPercentRoundedLikeTheServer() {
        Wallet wallet = wallet("50", "0");
        assertEquals(new BigDecimal("4.06"), wallet.feeFor(new BigDecimal("40.555")));
        assertEquals(new BigDecimal("36.50"), wallet.netFor(new BigDecimal("40.555")));
        assertEquals(new BigDecimal("5.00"), wallet.feeFor(new BigDecimal("50")));
        assertTrue(wallet.canWithdraw());
        assertFalse(wallet("4.99", "0").canWithdraw());
        assertFalse(wallet("50", "10").canWithdraw());
    }

    @Test
    void walkersWithdrawBetweenTheMinimumAndTheirBalanceOneAtATime() {
        Wallet wallet = wallet("50", "0");
        service.request(walker, wallet, new BigDecimal("40.555"));
        verify(repository).request(walker, new BigDecimal("40.56"));
        assertEquals("withdrawal.minimum", assertThrows(ValidationException.class,
                () -> service.request(walker, wallet, new BigDecimal("4.99"))).code());
        assertEquals("withdrawal.balance", assertThrows(ValidationException.class,
                () -> service.request(walker, wallet, new BigDecimal("50.01"))).code());
        assertEquals("withdrawal.pending", assertThrows(ValidationException.class,
                () -> service.request(walker, wallet("50", "10"), new BigDecimal("10"))).code());
        assertThrows(AccessDeniedException.class, () -> service.request(null, wallet, BigDecimal.TEN));
    }

    @Test
    void onlyAdministratorsPayAndNeverTheirOwnRequest() {
        Withdrawal request = withdrawal(walker.userId(), Withdrawal.Status.PENDING);
        assertThrows(AccessDeniedException.class, () -> service.markPaid(walker, request, "KB-123"));
        assertEquals("withdrawal.reference", assertThrows(ValidationException.class,
                () -> service.markPaid(admin, request, " 1 ")).code());
        service.markPaid(admin, request, " KB-TRX-1 ");
        verify(repository).review(admin, request.id(), true, "KB-TRX-1", "");

        Withdrawal own = withdrawal(admin.userId(), Withdrawal.Status.PENDING);
        assertThrows(AccessDeniedException.class, () -> service.markPaid(admin, own, "KB-TRX-2"));
        Withdrawal paid = withdrawal(walker.userId(), Withdrawal.Status.PAID);
        assertEquals("withdrawal.closed", assertThrows(ValidationException.class, () -> service.reject(admin, paid, "")).code());
        verify(repository, times(1)).review(any(), any(), anyBoolean(), anyString(), anyString());
    }

    @Test
    void receiptsShowANumberAndAMaskedIban() {
        Withdrawal paid = withdrawal(walker.userId(), Withdrawal.Status.PAID);
        assertEquals("MIYAV-000042", paid.receiptNumber());
        assertEquals("AZ21 •••• 1944", paid.maskedIban());
    }

    private static Wallet wallet(String available, String pending) {
        return new Wallet(new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal(pending), BigDecimal.ZERO, new BigDecimal(available), 1,
                BigDecimal.TEN, new BigDecimal("5"));
    }

    private static Withdrawal withdrawal(UUID walkerId, Withdrawal.Status status) {
        return new Withdrawal(UUID.randomUUID(), 42, walkerId, new BigDecimal("40.56"), BigDecimal.TEN, new BigDecimal("4.06"),
                new BigDecimal("36.50"), "Test Walker", "Kapital Bank", "AZ21NABZ00000000137010001944", status, "", "", null, NOW);
    }

    private static AuthSession session(UserRole role) {
        return new AuthSession(UUID.randomUUID(), "a@example.com", "t", "r", Instant.MAX, role);
    }
}
