package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.place.OpeningHours;
import az.miyav.domain.place.Place;
import az.miyav.domain.repository.PlaceRepository;
import az.miyav.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlacesServiceTest {
    // Tuesday 10 Nov 2026, 23:30 in Baku (UTC+4).
    private static final Instant TUESDAY_LATE = Instant.parse("2026-11-10T19:30:00Z");
    private static final LocalDateTime TUESDAY_NOON = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final LocalDateTime SUNDAY_NOON = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Test
    void openingHoursUnderstandTheCommonOsmForms() {
        assertEquals(Optional.of(true), OpeningHours.openAt("24/7", TUESDAY_NOON));
        assertEquals(Optional.of(true), OpeningHours.openAt("10:00-22:00", TUESDAY_NOON));
        assertEquals(Optional.of(false), OpeningHours.openAt("13:00-22:00", TUESDAY_NOON));
        assertEquals(Optional.of(true), OpeningHours.openAt("Mo-Fr 09:00-18:00; Sa 10:00-14:00", TUESDAY_NOON));
        assertEquals(Optional.of(false), OpeningHours.openAt("Mo-Fr 09:00-18:00; Sa 10:00-14:00; Su off", SUNDAY_NOON));
        assertEquals(Optional.of(true), OpeningHours.openAt("Mo-Su 09:00-00:00", LocalDateTime.of(2026, 11, 10, 23, 30)));
        assertEquals(Optional.of(true), OpeningHours.openAt("Mo-Su 18:00-02:00", LocalDateTime.of(2026, 11, 11, 1, 0)));
        assertEquals(Optional.empty(), OpeningHours.openAt("by appointment", TUESDAY_NOON));
        assertEquals(Optional.empty(), OpeningHours.openAt(null, TUESDAY_NOON));
    }

    @Test
    void filtersKeepOpenOrRoundTheClockPlacesNearestFirst() {
        PlaceRepository repository = mock(PlaceRepository.class);
        Place far24 = place("HappyVet", "24/7", 40.50, 49.90);
        Place nearDay = place("Day clinic", "Mo-Su 09:00-18:00", 40.41, 49.87);
        Place nearUnknown = place("Unknown", null, 40.4095, 49.8672);
        when(repository.list(Place.Kind.VET)).thenReturn(List.of(far24, nearDay, nearUnknown));
        PlacesService service = new PlacesService(repository, () -> TUESDAY_LATE);

        List<PlacesService.Nearby> all = service.nearest(Place.Kind.VET, 40.4093, 49.8671);
        assertEquals("Unknown", all.get(0).place().name());
        assertEquals(Optional.empty(), all.get(0).openNow());
        assertEquals(Optional.of(false), all.get(1).openNow());

        List<PlacesService.Nearby> open = service.nearest(Place.Kind.VET, 40.4093, 49.8671, PlacesService.Filter.OPEN_NOW);
        assertEquals(List.of("HappyVet"), open.stream().map(n -> n.place().name()).toList());
        assertEquals(1, service.nearest(Place.Kind.VET, 40.4093, 49.8671, PlacesService.Filter.ALWAYS_OPEN).size());
    }

    @Test
    void onlyAdminsMaintainThePlaces() {
        PlacesService service = new PlacesService(mock(PlaceRepository.class), Instant::now);
        AuthSession owner = new AuthSession(UUID.randomUUID(), "a@example.com", "t", "r", Instant.MAX, UserRole.OWNER);
        assertThrows(AccessDeniedException.class, () -> service.all(owner));
        assertThrows(AccessDeniedException.class, () -> service.save(owner, place("X", null, 40.4, 49.8)));
    }

    private static Place place(String name, String hours, double latitude, double longitude) {
        return new Place(UUID.randomUUID(), Place.Kind.VET, name, null, null, hours, latitude, longitude);
    }
}
