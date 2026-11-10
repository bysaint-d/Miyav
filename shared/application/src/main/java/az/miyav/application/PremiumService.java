package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.premium.PremiumRequest;
import az.miyav.domain.repository.PremiumRepository;
import az.miyav.security.RoleGuard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Premium listings: the owner pays by M10 or card, uploads the receipt and asks; an administrator checks the
 * receipt and approves or rejects. The server repeats every check below.
 */
public final class PremiumService {
    private static final int NOTE_LIMIT = 300;
    private final PremiumRepository premium;
    private final RoleGuard access;

    public PremiumService(PremiumRepository premium, RoleGuard access) {
        this.premium = premium;
        this.access = access;
    }

    public UUID request(AuthSession session, Pet listing, PremiumRequest.Method method, byte[] receiptJpeg) {
        requireSession(session);
        if (!listing.ownerId().equals(session.userId())) {
            throw new AccessDeniedException("access.owner");
        }
        if (listing.status() != PetStatus.PUBLISHED) {
            throw new ValidationException("premium.listing");
        }
        if (method == null) {
            throw new ValidationException("premium.method");
        }
        String path = premium.uploadReceipt(session, receiptJpeg);
        return premium.request(session, listing.id(), method, path);
    }

    public Optional<PremiumRequest> latest(AuthSession session, UUID listingId) {
        requireSession(session);
        return premium.latest(session, listingId);
    }

    public List<PremiumRequest> queue(AuthSession session, PremiumRequest.Status status) {
        requireAdmin(session);
        return premium.queue(session, status);
    }

    public void approve(AuthSession session, PremiumRequest request) {
        requireAdmin(session);
        requirePending(request);
        premium.review(session, request.id(), true, "");
    }

    public void reject(AuthSession session, PremiumRequest request, String note) {
        requireAdmin(session);
        requirePending(request);
        String text = note == null ? "" : note.trim();
        if (text.length() > NOTE_LIMIT) {
            throw new ValidationException("premium.note");
        }
        premium.review(session, request.id(), false, text);
    }

    /** The owner sees their own receipt; administrators see every receipt. */
    public byte[] receipt(AuthSession session, PremiumRequest request) {
        requireSession(session);
        return premium.receipt(session, request.receiptPath());
    }

    private static void requirePending(PremiumRequest request) {
        if (!request.pending()) {
            throw new ValidationException("premium.closed");
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
