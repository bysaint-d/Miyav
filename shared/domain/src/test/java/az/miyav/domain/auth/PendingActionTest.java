package az.miyav.domain.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingActionTest {
    @Test
    void survivesStorageWithItsArguments() {
        PendingAction action = PendingAction.of(ProtectedAction.APPLY_ADOPTION, "petId", "4b7c")
                .with("draft", "Salam | mən = həvəsliyəm 100%");

        PendingAction restored = PendingAction.decode(action.encode());

        assertEquals(action, restored);
        assertEquals("Salam | mən = həvəsliyəm 100%", restored.argument("draft"));
    }

    @Test
    void unknownOrEmptyValuesDecodeToNothing() {
        assertNull(PendingAction.decode(""));
        assertNull(PendingAction.decode(null));
        assertNull(PendingAction.decode("DELETE_EVERYTHING|x=1"));
    }

    @Test
    void matchesOnlyTheSameActionAndTarget() {
        PendingAction apply = PendingAction.of(ProtectedAction.APPLY_ADOPTION, "petId", "a");
        assertTrue(apply.matches(ProtectedAction.APPLY_ADOPTION, "petId", "a"));
        assertFalse(apply.matches(ProtectedAction.APPLY_ADOPTION, "petId", "b"));
        assertFalse(apply.matches(ProtectedAction.CONTACT_OWNER, "petId", "a"));
        assertEquals("guest.action.apply_adoption", ProtectedAction.APPLY_ADOPTION.promptKey());
    }
}
