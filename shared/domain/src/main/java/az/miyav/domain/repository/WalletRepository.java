package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.wallet.Wallet;
import az.miyav.domain.wallet.Withdrawal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The walker's MIYAV balance and withdrawals; the server computes every amount and the fee. */
public interface WalletRepository {
    Wallet wallet(AuthSession session);

    /** My own withdrawals, newest first. */
    List<Withdrawal> mine(AuthSession session);

    UUID request(AuthSession session, BigDecimal amount);

    void cancel(AuthSession session, UUID withdrawalId);

    /** Administrators: requests with the walker's name and balance; {@code status} null for all. */
    List<Withdrawal> queue(AuthSession session, Withdrawal.Status status);

    /** Administrators: paid with the bank transfer reference, or rejected with a reason. */
    void review(AuthSession session, UUID withdrawalId, boolean paid, String reference, String note);
}
