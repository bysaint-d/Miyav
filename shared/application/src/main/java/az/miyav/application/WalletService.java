package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.repository.WalletRepository;
import az.miyav.domain.wallet.Wallet;
import az.miyav.domain.wallet.Withdrawal;
import az.miyav.security.RoleGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * "MIYAV hesabım": the walker sees what they earned and asks to be paid; an administrator transfers the amount
 * minus the service fee and records the bank reference, which the walker gets as a receipt. The server repeats
 * every check below and is the only one that computes balances and fees.
 */
public final class WalletService {
    private static final int REFERENCE_MIN = 3;
    private static final int REFERENCE_MAX = 100;
    private static final int NOTE_MAX = 300;
    private final WalletRepository wallets;
    private final RoleGuard access;

    public WalletService(WalletRepository wallets, RoleGuard access) {
        this.wallets = wallets;
        this.access = access;
    }

    public Wallet wallet(AuthSession session) {
        requireSession(session);
        return wallets.wallet(session);
    }

    public List<Withdrawal> mine(AuthSession session) {
        requireSession(session);
        return wallets.mine(session);
    }

    /** Asks for {@code amount} of the available balance, checked against the wallet the walker just saw. */
    public UUID request(AuthSession session, Wallet wallet, BigDecimal amount) {
        requireSession(session);
        if (amount == null) {
            throw new ValidationException("withdrawal.minimum");
        }
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (wallet.pending().signum() > 0) {
            throw new ValidationException("withdrawal.pending");
        }
        if (value.compareTo(wallet.minimum()) < 0) {
            throw new ValidationException("withdrawal.minimum");
        }
        if (value.compareTo(wallet.available()) > 0) {
            throw new ValidationException("withdrawal.balance");
        }
        return wallets.request(session, value);
    }

    public void cancel(AuthSession session, Withdrawal withdrawal) {
        requireSession(session);
        if (!withdrawal.pending() || !withdrawal.walkerId().map(session.userId()::equals).orElse(false)) {
            throw new ValidationException("withdrawal.closed");
        }
        wallets.cancel(session, withdrawal.id());
    }

    public List<Withdrawal> queue(AuthSession session, Withdrawal.Status status) {
        requireAdmin(session);
        return wallets.queue(session, status);
    }

    /** Records the transfer: {@code reference} is the bank's transaction number shown on the walker's receipt. */
    public void markPaid(AuthSession session, Withdrawal withdrawal, String reference) {
        requireAdmin(session);
        requirePending(withdrawal);
        String value = reference == null ? "" : reference.trim();
        if (value.length() < REFERENCE_MIN || value.length() > REFERENCE_MAX) {
            throw new ValidationException("withdrawal.reference");
        }
        if (withdrawal.walkerId().map(session.userId()::equals).orElse(false)) {
            throw new AccessDeniedException("access.own_withdrawal");
        }
        wallets.review(session, withdrawal.id(), true, value, "");
    }

    public void reject(AuthSession session, Withdrawal withdrawal, String reason) {
        requireAdmin(session);
        requirePending(withdrawal);
        String note = reason == null ? "" : reason.trim();
        if (note.length() > NOTE_MAX) {
            throw new ValidationException("withdrawal.note");
        }
        wallets.review(session, withdrawal.id(), false, "", note);
    }

    private static void requirePending(Withdrawal withdrawal) {
        if (!withdrawal.pending()) {
            throw new ValidationException("withdrawal.closed");
        }
    }

    private void requireAdmin(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("access.admin");
        }
        access.requireAdmin(session.role());
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }
}
