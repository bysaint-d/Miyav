package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.payout.PayoutDetails;
import az.miyav.domain.repository.PayoutRepository;
import az.miyav.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PayoutServiceTest {
    private final PayoutRepository repository = mock(PayoutRepository.class);
    private final PayoutService service = new PayoutService(repository);
    private final AuthSession session = new AuthSession(UUID.randomUUID(), "a@example.com", "t", "r", Instant.MAX, UserRole.OWNER);

    @Test
    void savesNormalizedIbanWithTheCurrentAgreement() {
        service.save(session, "  Elmir Şəliyev ", "Kapital Bank", "az21 nabz 0000 0000 1370 1000 1944", true);
        verify(repository).save(session, "Elmir Şəliyev", "Kapital Bank", "AZ21NABZ00000000137010001944",
                PayoutDetails.AGREEMENT_VERSION);
    }

    @Test
    void checksEveryFieldAndTheAgreementBeforeSaving() {
        String iban = "AZ21NABZ00000000137010001944";
        assertEquals("payout.holder", assertThrows(ValidationException.class,
                () -> service.save(session, "El", "Kapital Bank", iban, true)).code());
        assertEquals("payout.bank", assertThrows(ValidationException.class,
                () -> service.save(session, "Elmir Şəliyev", "", iban, true)).code());
        assertEquals("payout.iban", assertThrows(ValidationException.class,
                () -> service.save(session, "Elmir Şəliyev", "Kapital Bank", "AZ00NABZ00000000137010001944", true)).code());
        assertEquals("payout.agreement", assertThrows(ValidationException.class,
                () -> service.save(session, "Elmir Şəliyev", "Kapital Bank", iban, false)).code());
        assertThrows(AccessDeniedException.class, () -> service.save(null, "Elmir Şəliyev", "Kapital Bank", iban, true));
        verifyNoInteractions(repository);
    }
}
