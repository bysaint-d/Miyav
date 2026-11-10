package az.miyav.domain.repository;

import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.premium.PremiumRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Premium listing requests. Owners upload a receipt and send a request; administrators read the queue and
 * decide. The server fixes price and length and checks the roles again.
 */
public interface PremiumRepository {
    /** Stores a JPEG receipt in the owner's private folder and returns its storage path. */
    String uploadReceipt(AuthSession session, byte[] jpeg);

    UUID request(AuthSession session, UUID listingId, PremiumRequest.Method method, String receiptPath);

    /** The owner's latest request for this listing, if any. */
    Optional<PremiumRequest> latest(AuthSession session, UUID listingId);

    /** Administrators: requests with {@code status} (all when null), pending first. */
    List<PremiumRequest> queue(AuthSession session, PremiumRequest.Status status);

    void review(AuthSession session, UUID requestId, boolean approve, String note);

    byte[] receipt(AuthSession session, String receiptPath);
}
