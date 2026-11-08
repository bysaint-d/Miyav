package az.miyav.application;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ValidationException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.repository.WalkRepository;
import az.miyav.domain.text.TextRules;
import az.miyav.domain.walk.WalkPet;
import az.miyav.domain.walk.WalkSession;
import az.miyav.domain.walk.Walker;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gəzinti: live walking sessions. The device's position is published only while a session is active;
 * pausing or ending removes it on the server, and other walkers only ever receive a rounded position.
 */
public final class WalkingService {
    public static final int MAX_PETS = 5;
    private final WalkRepository walks;

    public WalkingService(WalkRepository walks) {
        this.walks = walks;
    }

    public WalkSession start(AuthSession session, PetSpecies species, String petName, String message,
                             double latitude, double longitude) {
        requireSession(session);
        if (species == null) {
            throw new ValidationException("walk.species_required");
        }
        String name = TextRules.optional(petName, 60, "walk.text");
        String note = TextRules.optional(message, 120, "walk.text");
        requireArea(latitude, longitude);
        return walks.start(session, species, name, note, round(latitude), round(longitude));
    }

    /**
     * Starts a walk with the chosen pets: any of the walker's own pets (up to five) and/or the pet of a walk
     * job they were chosen for. At least one pet is needed so others see who is out walking.
     */
    public WalkSession startWith(AuthSession session, List<UUID> petIds, UUID walkListingId, String message,
                                 double latitude, double longitude) {
        requireSession(session);
        List<UUID> pets = petIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(petIds));
        if (pets.isEmpty() && walkListingId == null) {
            throw new ValidationException("walk.pet_required");
        }
        if (pets.size() > MAX_PETS) {
            throw new ValidationException("walk.pet_limit");
        }
        String note = TextRules.optional(message, 120, "walk.text");
        requireArea(latitude, longitude);
        return walks.startWith(session, pets, walkListingId, note, round(latitude), round(longitude));
    }

    /** The pets on a walk, for the map card. */
    public List<WalkPet> pets(AuthSession session, UUID sessionId) {
        requireSession(session);
        return walks.pets(session, sessionId);
    }

    public WalkSession updateLocation(AuthSession session, double latitude, double longitude) {
        requireSession(session);
        requireArea(latitude, longitude);
        return walks.updateLocation(session, round(latitude), round(longitude));
    }

    public WalkSession pause(AuthSession session) {
        requireSession(session);
        return walks.setStatus(session, WalkSession.Status.PAUSED);
    }

    public WalkSession resume(AuthSession session) {
        requireSession(session);
        return walks.setStatus(session, WalkSession.Status.ACTIVE);
    }

    public WalkSession end(AuthSession session) {
        requireSession(session);
        return walks.setStatus(session, WalkSession.Status.ENDED);
    }

    public Optional<WalkSession> current(AuthSession session) {
        requireSession(session);
        return walks.current(session);
    }

    /** Nearby walkers. Empty unless the caller is walking: location is shared both ways or not at all. */
    public List<Walker> nearby(AuthSession session) {
        requireSession(session);
        return walks.nearby(session);
    }

    private static void requireArea(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude) || !ListingLocation.inArea(latitude, longitude)) {
            throw new ValidationException("location.out_of_area");
        }
    }

    private static double round(double value) {
        return Math.round(value * 100000.0) / 100000.0;
    }

    private static void requireSession(AuthSession session) {
        if (session == null) {
            throw new AccessDeniedException("auth.session_expired");
        }
    }
}
