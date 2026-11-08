package az.miyav.domain.map;

import az.miyav.domain.location.GeoPoint;
import az.miyav.domain.walk.WalkActivity;
import az.miyav.domain.walk.WalkSession;
import az.miyav.domain.pet.PetSpecies;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPointTest {
    private static final GeoPoint BAKU = new GeoPoint(40.41, 49.87);

    @Test
    void eachKindOfPointKnowsHowPreciseItMayBe() {
        List<MapPoint> points = List.of(
                MapPoint.of(MapPointKind.LIVE_WALK, UUID.randomUUID(), "Elmir", "Boncuk · dog", BAKU, false),
                MapPoint.of(MapPointKind.WALK_HELPER, UUID.randomUUID(), "Leyla", "Yasamal", BAKU, true),
                MapPoint.of(MapPointKind.ADOPTION, UUID.randomUUID(), "Mestan", "cat · Bakı", BAKU, false));

        assertInstanceOf(LiveWalkPoint.class, points.get(0));
        assertInstanceOf(WalkHelperPoint.class, points.get(1));
        assertInstanceOf(AdoptionPoint.class, points.get(2));
        assertEquals(List.of(1000, 1000, 100), points.stream().map(MapPoint::privacyRadiusMeters).toList());
        assertTrue(((WalkHelperPoint) points.get(1)).verified());
    }

    @Test
    void unknownKindsAreRejected() {
        assertEquals(MapPointKind.WALK_HELPER, MapPointKind.fromWire("helper"));
        assertThrows(IllegalArgumentException.class, () -> MapPointKind.fromWire("home"));
    }

    @Test
    void anEndedWalkNeverKeepsAPosition() {
        WalkActivity ended = new WalkActivity(UUID.randomUUID(), UUID.randomUUID(), "Elmir", "Boncuk", PetSpecies.DOG,
                WalkSession.Status.ENDED, "", Instant.EPOCH, Instant.EPOCH.plusSeconds(600), BAKU);
        WalkActivity live = new WalkActivity(UUID.randomUUID(), UUID.randomUUID(), "Elmir", "Boncuk", PetSpecies.DOG,
                WalkSession.Status.ACTIVE, "", Instant.EPOCH, null, BAKU);

        assertTrue(ended.approximatePosition().isEmpty());
        assertTrue(live.approximatePosition().isPresent());
        assertTrue(live.live());
    }
}
