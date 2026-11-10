package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.payout.PayoutDetails;
import az.miyav.domain.repository.PayoutRepository;

import java.util.Optional;

/**
 * "Müqavilə və bank məlumatları": the walker's account holder, bank and IBAN, saved together with acceptance of
 * the current walker agreement. The database function checks the same rules again.
 */
public final class PayoutService {
    private final PayoutRepository payouts;

    public PayoutService(PayoutRepository payouts) {
        this.payouts = payouts;
    }

    public Optional<PayoutDetails> mine(AuthSession session) {
        requireSession(session);
        return payouts.mine(session);
    }

    public void save(AuthSession session, String holderName, String bankName, String iban, boolean agreementAccepted) {
        requireSession(session);
        String holder = holderName == null ? "" : holderName.trim();
        String bank = bankName == null ? "" : bankName.trim();
        if (holder.length() < 3 || holder.length() > 100) {
            throw new ValidationException("payout.holder");
        }
        if (bank.length() < 2 || bank.length() > 80) {
            throw new ValidationException("payout.bank");
        }
        if (!PayoutDetails.validIban(iban)) {
            throw new ValidationException("payout.iban");
        }
        if (!agreementAccepted) {
            throw new ValidationException("payout.agreement");
        }
        payouts.save(session, holder, bank, PayoutDetails.normalizeIban(iban), PayoutDetails.AGREEMENT_VERSION);
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.required");
        }
    }
}
