package az.miyav.android;

import az.miyav.application.AdoptionService;
import az.miyav.application.AuthService;
import az.miyav.application.PetService;
import az.miyav.application.UserService;
import az.miyav.application.WalkingService;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.pet.Vaccination;
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.walk.WalkSession;
import az.miyav.domain.walk.Walker;
import az.miyav.common.error.AppException;
import az.miyav.common.time.SystemTimeSource;
import az.miyav.data.MiyavBackend;
import az.miyav.data.http.HttpExchange;
import az.miyav.data.http.OkHttpTransport;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetQuery;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.user.User;
import az.miyav.domain.validation.EmailValidator;
import az.miyav.domain.validation.PasswordValidator;
import az.miyav.domain.validation.PetValidator;
import az.miyav.domain.validation.ProfileValidator;
import az.miyav.security.MemorySessionStore;
import az.miyav.security.RoleGuard;

import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

/**
 * Drives the hosted Supabase project through the same services the app uses. Runs only when the QA
 * credentials are provided through environment variables; otherwise it is skipped.
 */
public class LiveSupabaseFlowTest {
    private static final byte[] TINY_JPEG = Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=");

    private MiyavBackend backend;
    private String ownerEmail;
    private String ownerPassword;
    private String applicantEmail;
    private String applicantPassword;

    @Before
    public void configure() {
        String url = env("MIYAV_SUPABASE_URL");
        String key = env("MIYAV_SUPABASE_PUBLISHABLE_KEY");
        ownerEmail = env("MIYAV_IT_OWNER_EMAIL");
        ownerPassword = env("MIYAV_IT_OWNER_PASSWORD");
        applicantEmail = env("MIYAV_IT_APPLICANT_EMAIL");
        applicantPassword = env("MIYAV_IT_APPLICANT_PASSWORD");
        assumeFalse("Live QA credentials not provided", url.isEmpty() || key.isEmpty()
                || ownerEmail.isEmpty() || ownerPassword.isEmpty()
                || applicantEmail.isEmpty() || applicantPassword.isEmpty());
        backend = MiyavBackend.connect(url, key);
    }

    @Test
    public void sessionLifecycleWorksAgainstHostedAuth() {
        MemorySessionStore store = new MemorySessionStore();
        AuthService auth = auth(store);

        AuthSession session = auth.login(ownerEmail, ownerPassword);
        assertTrue(store.load().isPresent());
        assertEquals(ownerEmail, session.email());

        store.save(session.withTokens(session.accessToken(), session.refreshToken(), Instant.now().minusSeconds(5)));
        AuthSession restored = auth.restore().orElseThrow();
        assertNotEquals(session.accessToken(), restored.accessToken());
        assertEquals(session.role(), restored.role());

        AuthSession forCheck = store.load().orElseThrow();
        auth.logout();
        assertFalse(store.load().isPresent());
        try {
            backend.auth().refresh(forCheck.refreshToken());
            fail("A refresh token must stop working after logout");
        } catch (AppException expected) {
            assertTrue(expected.code().startsWith("auth."));
        }

        try {
            auth.login(ownerEmail, ownerPassword + "x");
            fail("A wrong password must be rejected");
        } catch (AppException expected) {
            assertEquals("auth.failed", expected.code());
        }
        System.out.println("LIVE: login, refresh-on-restore, logout revocation and wrong-password rejection verified");
    }

    @Test
    public void listingAdoptionAndRlsFlowWorksAgainstHostedDatabase() {
        MemorySessionStore ownerStore = new MemorySessionStore();
        MemorySessionStore applicantStore = new MemorySessionStore();
        AuthService ownerAuth = auth(ownerStore);
        AuthService applicantAuth = auth(applicantStore);
        AuthSession owner = ownerAuth.login(ownerEmail, ownerPassword);
        AuthSession applicant = applicantAuth.login(applicantEmail, applicantPassword);

        RoleGuard guard = new RoleGuard();
        PetValidator validator = new PetValidator();
        UserService users = new UserService(backend.users(), new ProfileValidator(), backend.media());
        PetService pets = new PetService(backend.pets(), backend.media(), validator, guard);
        AdoptionService adoptions = new AdoptionService(backend.adoptions(), validator, guard, new SystemTimeSource());

        User ownerUser = users.updateProfile(owner, "QA Owner", "Bakı", "Nəsimi", "");
        assertEquals("Nəsimi", ownerUser.district());
        User applicantUser = users.current(applicant);

        String marker = "QA" + UUID.randomUUID().toString().substring(0, 8);
        ListingLocation place = new ListingLocation(40.37712, 49.85391, "QA test area, Bakı");
        Pet draft = pets.createDraft(owner, PetSpecies.DOG, marker, "Golden Retriever", 14, PetGender.FEMALE, "Bakı",
                "Automated QA listing. It is archived at the end of the test.", Vaccination.YES, null);
        assertEquals(PetStatus.DRAFT, draft.status());
        Pet withPhoto = pets.addPhoto(owner, draft, TINY_JPEG);
        assertEquals(1, withPhoto.photoPaths().size());
        assertArrayEquals(TINY_JPEG, pets.photo(owner, withPhoto.photoPaths().get(0)));
        try {
            pets.publish(owner, withPhoto);
            fail("Publishing without a location must be refused");
        } catch (AppException expected) {
            assertEquals("pet.location_required", expected.code());
        }
        Pet located = pets.update(owner, withPhoto, marker, "Golden Retriever", 14, PetGender.FEMALE, "Bakı",
                withPhoto.description(), PetStatus.DRAFT, Vaccination.YES, place);
        assertEquals(40.377, located.location().latitude(), 0.0000001);
        assertEquals(49.854, located.location().longitude(), 0.0000001);
        assertEquals("QA test area, Bakı", located.location().name());
        assertEquals(Vaccination.YES, located.vaccination());
        assertEquals(PetGender.FEMALE, located.gender());
        assertEquals("Golden Retriever", located.breed());
        Pet published = pets.publish(owner, located);
        assertEquals(PetStatus.PUBLISHED, published.status());

        List<Pet> found = pets.discover(new PetQuery(null, "Bakı", marker, 20, 0), applicant);
        assertTrue(found.stream().anyMatch(pet -> pet.id().equals(published.id())));
        assertArrayEquals(TINY_JPEG, pets.photo(applicant, published.photoPaths().get(0)));
        List<Pet> near = pets.near(applicant, 40.38, 49.86, 5, new PetQuery(null, "", marker, 20, 0));
        assertTrue(near.stream().anyMatch(pet -> pet.id().equals(published.id())));
        assertTrue(pets.near(applicant, 40.59, 49.67, 5, new PetQuery(null, "", marker, 20, 0)).isEmpty());

        PublicProfile hiddenPhone = users.publicProfile(applicant, owner.userId()).orElseThrow();
        assertEquals("QA Owner", hiddenPhone.fullName());
        assertFalse(hiddenPhone.contactDigits().isPresent());
        users.updateProfile(owner, "QA Owner", "Bakı", "Nəsimi", "+994 50 000 00 00", "QA account for automated tests.", true);
        PublicProfile shownPhone = users.publicProfile(applicant, owner.userId()).orElseThrow();
        assertEquals("994500000000", shownPhone.contactDigits().orElseThrow());
        users.updateProfile(owner, "QA Owner", "Bakı", "Nəsimi", "", "", false);
        assertFalse(users.publicProfile(applicant, owner.userId()).orElseThrow().contactDigits().isPresent());
        assertTrue(pets.byPublisher(applicant, owner.userId()).stream().anyMatch(pet -> pet.id().equals(published.id())));

        try {
            backend.pets().save(applicant, published.withStatus(PetStatus.ARCHIVED));
            fail("RLS must stop another user from changing the listing");
        } catch (AppException expected) {
            assertEquals(PetStatus.PUBLISHED, pets.find(published.id(), owner).orElseThrow().status());
        }

        AdoptionApplication application = adoptions.submit(applicant, applicantUser, published,
                "Automated QA application: I have a safe home and time for a dog.");
        assertEquals(ApplicationStatus.PENDING, application.status());
        try {
            adoptions.submit(applicant, applicantUser, published, "A second pending application must be refused.");
            fail("Duplicate pending application must be refused");
        } catch (AppException expected) {
            assertEquals("adoption.duplicate", expected.code());
        }
        try {
            backend.adoptions().save(owner, new AdoptionApplication(UUID.randomUUID(), published.id(), owner.userId(),
                    "Owner tries to apply to their own listing directly.", ApplicationStatus.PENDING, Instant.now(), Instant.now()));
            fail("RLS must refuse an application to one's own listing");
        } catch (AppException expected) {
            assertTrue(expected.code().length() > 0);
        }

        assertTrue(adoptions.forPet(owner, published).stream().anyMatch(row -> row.id().equals(application.id())));
        try {
            adoptions.accept(applicant, applicantUser, published, application);
            fail("Only the owner may accept");
        } catch (AppException expected) {
            assertEquals("access.owner", expected.code());
        }
        AdoptionApplication accepted = adoptions.accept(owner, ownerUser, published, application);
        assertEquals(ApplicationStatus.ACCEPTED, accepted.status());
        assertEquals(PetStatus.ADOPTED, pets.find(published.id(), owner).orElseThrow().status());
        assertTrue(adoptions.mine(applicant).stream()
                .anyMatch(row -> row.id().equals(application.id()) && row.status() == ApplicationStatus.ACCEPTED));

        Pet archived = pets.archive(owner, pets.find(published.id(), owner).orElseThrow());
        assertEquals(PetStatus.ARCHIVED, archived.status());
        assertFalse(pets.discover(new PetQuery(null, "", marker, 20, 0), applicant).stream()
                .anyMatch(pet -> pet.id().equals(published.id())));

        ownerAuth.logout();
        applicantAuth.logout();
        System.out.println("LIVE: profile, draft, photo upload/download, publish, discovery, RLS write denial, "
                + "application, duplicate block, own-listing block, accept, adopted status and archive verified");
    }

    @Test
    public void walkingSessionsShareOnlyApproximateLocationBetweenActiveWalkers() {
        MemorySessionStore ownerStore = new MemorySessionStore();
        MemorySessionStore applicantStore = new MemorySessionStore();
        AuthService ownerAuth = auth(ownerStore);
        AuthService applicantAuth = auth(applicantStore);
        AuthSession owner = ownerAuth.login(ownerEmail, ownerPassword);
        AuthSession applicant = applicantAuth.login(applicantEmail, applicantPassword);
        WalkingService walking = new WalkingService(backend.walks());
        UserService users = new UserService(backend.users(), new ProfileValidator(), backend.media());
        try {
            safeEnd(walking, owner);
            safeEnd(walking, applicant);
            assertTrue("A user who is not walking sees nobody", walking.nearby(applicant).isEmpty());

            WalkSession ownerWalk = walking.start(owner, PetSpecies.DOG, "QA Boncuk", "QA status", 40.40931, 49.86715);
            assertEquals(WalkSession.Status.ACTIVE, ownerWalk.status());
            assertTrue("Still nobody while the viewer is not walking", walking.nearby(applicant).isEmpty());
            if (!hasActiveListing(applicant, owner.userId())) {
                assertFalse("Walker profile is hidden from non-walkers",
                        users.publicProfile(applicant, owner.userId()).isPresent());
            }

            walking.start(applicant, PetSpecies.CAT, "QA Mestan", "", 40.41381, 49.86700);
            List<Walker> seen = walking.nearby(applicant);
            Walker ownerSeen = seen.stream().filter(walker -> walker.userId().equals(owner.userId())).findFirst().orElseThrow();
            assertEquals("Coordinates are rounded to 3 decimals", 40.409, ownerSeen.latitude(), 0.0000001);
            assertEquals(49.867, ownerSeen.longitude(), 0.0000001);
            assertEquals("Distance is rounded to 100 m", 0, ownerSeen.distanceMeters() % 100);
            assertTrue(ownerSeen.distanceMeters() >= 400 && ownerSeen.distanceMeters() <= 600);
            assertEquals(PetSpecies.DOG, ownerSeen.species());
            assertEquals("QA Boncuk", ownerSeen.petName());
            assertTrue(walking.nearby(owner).stream().anyMatch(walker -> walker.userId().equals(applicant.userId())));
            assertTrue("Walking users can open each other's profile",
                    users.publicProfile(applicant, owner.userId()).isPresent());

            walking.updateLocation(owner, 40.41000, 49.86800);
            assertEquals(WalkSession.Status.ACTIVE, walking.current(owner).orElseThrow().status());

            walking.pause(owner);
            assertFalse("Paused walkers are hidden", walking.nearby(applicant).stream()
                    .anyMatch(walker -> walker.userId().equals(owner.userId())));
            try {
                walking.updateLocation(owner, 40.41, 49.868);
                fail("A paused session must not accept location updates");
            } catch (AppException expected) {
                assertEquals("walk.no_session", expected.code());
            }
            walking.resume(owner);
            walking.updateLocation(owner, 40.41000, 49.86800);
            assertTrue(walking.nearby(applicant).stream().anyMatch(walker -> walker.userId().equals(owner.userId())));

            walking.end(owner);
            assertFalse("Ended walkers are hidden", walking.nearby(applicant).stream()
                    .anyMatch(walker -> walker.userId().equals(owner.userId())));
            assertFalse(walking.current(owner).isPresent());
            try {
                walking.start(owner, PetSpecies.DOG, "", "", 39.237, -123.15);
                fail("Coordinates outside the service area are refused");
            } catch (AppException expected) {
                assertEquals("location.out_of_area", expected.code());
            }

            HttpExchange direct = rest("/rest/v1/walk_sessions?select=id,user_id&user_id=eq." + applicant.userId(), owner);
            assertEquals(200, direct.status());
            assertEquals("RLS hides another user's walk sessions", "[]", direct.body().trim());
            HttpExchange locations = rest("/rest/v1/walk_locations?select=*", owner);
            assertTrue("Live coordinates are not exposed through the Data API", locations.status() >= 400);
        } finally {
            safeEnd(walking, owner);
            safeEnd(walking, applicant);
            ownerAuth.logout();
            applicantAuth.logout();
        }
        System.out.println("LIVE: walk start, reciprocal nearby visibility, ~100 m rounding, pause hides, resume shows, "
                + "end hides, out-of-area refused, RLS on walk_sessions, walk_locations not exposed");
    }

    private static HttpExchange rest(String path, AuthSession session) {
        String base = env("MIYAV_SUPABASE_URL").replaceAll("/+$", "");
        return new OkHttpTransport().send("GET", URI.create(base + path), Map.of(
                "apikey", env("MIYAV_SUPABASE_PUBLISHABLE_KEY"),
                "Authorization", "Bearer " + session.accessToken()), null);
    }

    private boolean hasActiveListing(AuthSession viewer, UUID owner) {
        return !backend.pets().findPublishedByOwner(owner, viewer).isEmpty();
    }

    private static void safeEnd(WalkingService walking, AuthSession session) {
        try {
            walking.end(session);
        } catch (AppException ignored) {
            // No open session.
        }
    }

    @Test
    public void passwordChangeAndRecoveryRequestWorkAgainstHostedAuth() {
        MemorySessionStore store = new MemorySessionStore();
        AuthService auth = auth(store);
        auth.login(applicantEmail, applicantPassword);
        String temporary = applicantPassword + "Q9";
        try {
            auth.changePassword(applicantPassword + "wrong", temporary);
            fail("A wrong current password must be refused");
        } catch (AppException expected) {
            assertEquals("auth.current_password", expected.code());
        }
        auth.changePassword(applicantPassword, temporary);
        try {
            auth.logout();
            auth.login(applicantEmail, temporary);
        } finally {
            if (auth.current().isEmpty()) {
                auth.login(applicantEmail, temporary);
            }
            auth.changePassword(temporary, applicantPassword);
        }
        auth.logout();
        auth.login(applicantEmail, applicantPassword);
        auth.logout();
        System.out.println("LIVE: in-app password change verified and restored");

        try {
            auth.requestPasswordReset(applicantEmail, "az.miyav.native://auth");
            System.out.println("LIVE: password recovery email request accepted by Supabase Auth");
        } catch (AppException exception) {
            assertEquals("auth.rate_limited", exception.code());
            System.out.println("LIVE: password recovery request reached Supabase Auth but was rate limited (auth.rate_limited)");
        }
    }

    private AuthService auth(MemorySessionStore store) {
        return new AuthService(backend.auth(), backend.users(), store, new EmailValidator(), new PasswordValidator(), new SystemTimeSource());
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }
}
