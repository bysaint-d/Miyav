package az.miyav.application;

import az.miyav.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationServiceTest {
    private final LocationService locations = new LocationService();

    @Test
    void roundsToTheStoredPrecisionInsideTheSupportedArea() {
        var point = locations.approximate(40.4093, 49.8671);
        assertEquals(40.41, point.latitude());
        assertEquals(49.87, point.longitude());
    }

    @Test
    void rejectsCoordinatesOutsideTheExistingBoundingBox() {
        assertThrows(ValidationException.class, () -> locations.approximate(10, 10));
    }

    @Test
    void mapsApproximateCoordinatesToASupportedCity() {
        assertEquals("Bakı", locations.nearestCity(40.4093, 49.8671));
        assertEquals("Sumqayıt", locations.nearestCity(40.59, 49.67));
    }
}
