package az.miyav.domain.pet;

import az.miyav.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PetProfileDraftTest {
    @Test
    void aValidDraftIsTrimmedAndRounded() {
        PetProfileDraft draft = new PetProfileDraft("  Boncuk ", PetSpecies.DOG, " Labrador ", new BigDecimal("2.55"),
                new BigDecimal("12.04"), "  Sakitdir  ");
        assertEquals("Boncuk", draft.name());
        assertEquals("Labrador", draft.breed());
        assertEquals(new BigDecimal("2.6"), draft.ageYears());
        assertEquals(new BigDecimal("12.0"), draft.weightKg());
        assertEquals("Sakitdir", draft.careNotes());
    }

    @Test
    void theTableLimitsAreCheckedBeforeSaving() {
        assertEquals("petprofile.name", code(() -> new PetProfileDraft(" ", PetSpecies.CAT, "", BigDecimal.ONE, BigDecimal.ONE, "")));
        assertEquals("petprofile.age", code(() -> new PetProfileDraft("Mırmır", PetSpecies.CAT, "", new BigDecimal("81"), BigDecimal.ONE, "")));
        assertEquals("petprofile.age", code(() -> new PetProfileDraft("Mırmır", PetSpecies.CAT, "", null, BigDecimal.ONE, "")));
        assertEquals("petprofile.weight", code(() -> new PetProfileDraft("Mırmır", PetSpecies.CAT, "", BigDecimal.ONE, BigDecimal.ZERO, "")));
        assertEquals("petprofile.breed", code(() -> new PetProfileDraft("Mırmır", PetSpecies.CAT, "x".repeat(81), BigDecimal.ONE, BigDecimal.ONE, "")));
    }

    private static String code(Runnable action) {
        return assertThrows(ValidationException.class, action::run).code();
    }
}
