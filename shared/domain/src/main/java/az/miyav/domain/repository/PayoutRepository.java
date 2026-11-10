package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.payout.PayoutDetails;

import java.util.Optional;

/** The signed-in member's own payout details; nobody else but administrators can read them. */
public interface PayoutRepository {
    Optional<PayoutDetails> mine(AuthSession session);

    /** Creates or replaces the details and records acceptance of {@code agreementVersion}. */
    void save(AuthSession session, String holderName, String bankName, String iban, String agreementVersion);
}
