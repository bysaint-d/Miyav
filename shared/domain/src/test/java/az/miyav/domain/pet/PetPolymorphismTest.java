package az.miyav.domain.pet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetPolymorphismTest {
    @Test
    void factoryChoosesASpeciesSpecificCareProfile() {
        Pet dog = pet(PetSpecies.DOG);
        Pet cat = pet(PetSpecies.CAT);
        assertInstanceOf(Dog.class, dog);
        assertInstanceOf(Cat.class, cat);
        assertTrue(dog.defaultCareProfile().needsOutdoorTime());
        assertFalse(cat.defaultCareProfile().needsOutdoorTime());
        assertTrue(cat.defaultCareProfile().typicallyLitterTrained());
        assertEquals("pet.care.dog", dog.defaultCareProfile().labelKey());
        assertEquals("pet.care.cat", cat.defaultCareProfile().labelKey());
    }

    private static Pet pet(PetSpecies species) {
        return PetFactory.create(
                species,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Boncuk",
                "mix",
                14,
                PetGender.FEMALE,
                "Bakı",
                "Sakit və mehriban bir heyvandır.",
                PetStatus.PUBLISHED,
                List.of("photo.jpg")
        );
    }
}
