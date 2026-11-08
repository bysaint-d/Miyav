package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.ConfigurationException;
import az.miyav.common.error.PersistenceException;
import az.miyav.common.locale.LocaleCode;
import az.miyav.data.http.HttpExchange;
import az.miyav.data.http.HttpTransport;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabaseContractTest {
    @Test
    void publishableConfigurationRejectsSecretsAndPlainHttp() {
        assertTrue(SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test").configured());
        assertThrows(ConfigurationException.class, () -> SupabaseConfig.parse("https://example.supabase.co", "sb_secret_test"));
        assertThrows(ConfigurationException.class, () -> SupabaseConfig.parse("https://example.supabase.co", serviceRoleJwt()));
        assertThrows(ConfigurationException.class, () -> SupabaseConfig.parse("http://example.com", "sb_publishable_test"));
        assertEquals("SupabaseConfig{configured=true}", SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test").toString());
    }

    @Test
    void googleUrlAndPasswordSessionUseTheAuthContract() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        SupabaseClient client = new SupabaseClient(config, transport);
        SupabaseAuthRepository auth = new SupabaseAuthRepository(config, client);

        assertEquals(
                "https://example.supabase.co/auth/v1/authorize?provider=google&redirect_to=miyav%3A%2F%2Fauth",
                auth.googleAuthorizeUri("miyav://auth").toString()
        );
        UUID userId = UUID.randomUUID();
        transport.body = "{\"access_token\":\"secret-token\",\"refresh_token\":\"refresh\",\"expires_in\":3600,\"user\":{\"id\":\"" + userId + "\",\"email\":\"a@example.com\"}}";
        AuthSession session = auth.signIn("a@example.com", "şifrə12345");
        assertEquals(userId, session.userId());
        assertTrue(transport.path.contains("/auth/v1/token?grant_type=password"));
        assertTrue(transport.headers.get("apikey").equals("sb_publishable_test"));
        assertFalse(session.toString().contains("secret-token"));
        assertFalse(transport.requestBody.contains("secret-token"));
    }

    @Test
    void failedPasswordGrantDoesNotBecomeASession() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        transport.status = 400;
        transport.body = "{\"error\":\"invalid_grant\"}";
        SupabaseAuthRepository auth = new SupabaseAuthRepository(config, new SupabaseClient(config, transport));
        assertThrows(PersistenceException.class, () -> auth.signIn("a@example.com", "şifrə12345"));
    }

    @Test
    void listingStatusAndSearchStayInsideTheLiveContract() {
        assertEquals(PetStatus.PUBLISHED, ListingStatusMapper.toDomain("active"));
        assertEquals(PetStatus.ARCHIVED, ListingStatusMapper.toDomain("deleted"));
        assertEquals("Boncukdrop", SupabasePetRepository.sanitize("Boncuk;drop"));
        assertEquals("", SupabasePetRepository.sanitize("(),"));
    }

    @Test
    void adoptionAndLanguageUseTheMilestone4Contract() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        SupabaseClient client = new SupabaseClient(config, transport);
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        AuthSession session = new AuthSession(userId, "a@example.com", "secret-token", "refresh", Instant.parse("2026-11-08T01:00:00Z"), UserRole.OWNER);
        transport.body = "[{\"id\":\"" + applicationId + "\",\"listing_id\":\"" + listingId + "\",\"applicant_id\":\"" + userId + "\",\"message\":\"Mən bu itə ev vermək istəyirəm.\",\"status\":\"pending\",\"created_at\":\"2026-11-08T00:00:00+00:00\",\"updated_at\":\"2026-11-08T00:00:00+00:00\"}]";
        SupabaseAdoptionRepository adoptions = new SupabaseAdoptionRepository(client);
        AdoptionApplication pending = new AdoptionApplication(
                applicationId,
                listingId,
                userId,
                "Mən bu itə ev vermək istəyirəm.",
                ApplicationStatus.PENDING,
                Instant.parse("2026-11-08T00:00:00Z"),
                Instant.parse("2026-11-08T00:00:00Z")
        );
        adoptions.save(session, pending);
        assertTrue(transport.path.endsWith("/rest/v1/adoption_applications"));
        assertTrue(transport.requestBody.contains("listing_id"));
        assertFalse(transport.requestBody.contains("applicant_id"));
        assertFalse(transport.requestBody.contains("status"));

        transport.body = "{\"id\":\"" + applicationId + "\",\"listing_id\":\"" + listingId + "\",\"applicant_id\":\"" + userId + "\",\"message\":\"Mən bu itə ev vermək istəyirəm.\",\"status\":\"accepted\",\"created_at\":\"2026-11-08T00:00:00+00:00\",\"updated_at\":\"2026-11-08T00:00:01+00:00\"}";
        assertEquals(ApplicationStatus.ACCEPTED, adoptions.save(session, pending.withStatus(ApplicationStatus.ACCEPTED, Instant.parse("2026-11-08T00:00:01Z"))).status());
        assertTrue(transport.path.contains("/rest/v1/rpc/accept_adoption_application"));

        transport.status = 400;
        transport.body = "{\"code\":\"P0001\",\"message\":\"FORBIDDEN\"}";
        assertThrows(AccessDeniedException.class, () -> adoptions.save(session, pending.withStatus(ApplicationStatus.REJECTED, Instant.parse("2026-11-08T00:00:01Z"))));

        transport.status = 200;
        transport.body = "[{\"id\":\"" + userId + "\",\"full_name\":\"Aysel Məmmədova\",\"city\":\"Bakı\",\"district\":\"\",\"phone\":\"\",\"role\":\"owner\",\"suspended\":false,\"preferred_language\":\"ru\"}]";
        SupabaseUserRepository users = new SupabaseUserRepository(client);
        assertEquals(LocaleCode.RU, users.findById(session).orElseThrow().preferredLocale());
        users.updateProfile(session, users.findById(session).orElseThrow().withLocale(LocaleCode.EN));
        assertTrue(transport.requestBody.contains("\"preferred_language\":\"en\""));
        assertFalse(transport.requestBody.contains("\"role\""));
    }

    @Test
    void refreshAndListingWritesStayInsideTheLiveContract() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        SupabaseClient client = new SupabaseClient(config, transport);
        UUID userId = UUID.randomUUID();
        transport.body = "{\"access_token\":\"new-token\",\"refresh_token\":\"refresh-2\",\"expires_in\":3600,\"user\":{\"id\":\"" + userId + "\",\"email\":\"a@example.com\"}}";
        AuthSession refreshed = new SupabaseAuthRepository(config, client).refresh("refresh");
        assertTrue(transport.path.contains("grant_type=refresh_token"));
        assertEquals("new-token", refreshed.accessToken());

        UUID listingId = UUID.randomUUID();
        AuthSession session = new AuthSession(userId, "a@example.com", "secret-token", "refresh", Instant.parse("2026-11-08T01:00:00Z"), UserRole.OWNER);
        transport.body = "[{\"id\":\"" + listingId + "\",\"owner_id\":\"" + userId + "\",\"name\":\"Boncuk\",\"species\":\"dog\",\"breed\":\"mix\",\"age_months\":12,\"gender\":\"female\",\"city\":\"Bakı\",\"description\":\"Sakit və mehriban bir itdir.\",\"status\":\"draft\",\"photos\":[]}]";
        az.miyav.domain.pet.Pet pet = az.miyav.domain.pet.PetFactory.create(
                az.miyav.domain.pet.PetSpecies.DOG,
                listingId,
                userId,
                "Boncuk",
                "mix",
                12,
                az.miyav.domain.pet.PetGender.FEMALE,
                "Bakı",
                "Sakit və mehriban bir itdir.",
                PetStatus.DRAFT,
                java.util.List.of()
        );
        new SupabasePetRepository(client).save(session, pet);
        assertTrue(transport.path.endsWith("/rest/v1/adoption_listings") || transport.path.contains("id=eq."));
        assertFalse(transport.requestBody.contains("owner_id"));
        assertTrue(transport.requestBody.contains("\"status\":\"draft\""));
    }

    @Test
    void listingLocationNearbyAndWalkingUseTheMigrationContract() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        SupabaseClient client = new SupabaseClient(config, transport);
        UUID userId = UUID.randomUUID();
        AuthSession session = new AuthSession(userId, "a@example.com", "secret-token", "refresh", Instant.parse("2026-11-08T01:00:00Z"), UserRole.OWNER);

        az.miyav.domain.pet.Pet pet = az.miyav.domain.pet.PetFactory.create(
                az.miyav.domain.pet.PetSpecies.CAT, UUID.randomUUID(), userId, "Mestan", "British Shorthair", 10,
                az.miyav.domain.pet.PetGender.MALE, "Bakı", "Sakit və ev şəraitinə öyrəşmiş pişik.", PetStatus.DRAFT,
                java.util.List.of(),
                new az.miyav.domain.pet.ListingExtras(az.miyav.domain.pet.Vaccination.YES,
                        new az.miyav.domain.location.ListingLocation(40.409312, 49.867154, "Nəsimi, Bakı")));
        String body = SupabasePetRepository.listingBody(pet, false).toString();
        assertTrue(body.contains("\"latitude\":40.409"));
        assertTrue(body.contains("\"longitude\":49.867"));
        assertTrue(body.contains("\"vaccination\":\"yes\""));
        assertTrue(body.contains("\"gender\":\"male\""));
        assertTrue(body.contains("\"breed\":\"British Shorthair\""));

        transport.body = "[]";
        new SupabasePetRepository(client).findNear(40.4, 49.8, 10, az.miyav.domain.pet.PetQuery.firstPage(), session);
        assertTrue(transport.path.endsWith("/rest/v1/rpc/listings_near"));
        assertTrue(transport.requestBody.contains("\"p_radius_km\":10"));

        transport.body = "{\"id\":\"" + UUID.randomUUID() + "\",\"status\":\"active\",\"pet_species\":\"dog\",\"pet_name\":\"Boncuk\",\"status_message\":\"\",\"started_at\":\"2026-11-08T00:00:00+00:00\",\"expires_at\":\"2026-11-08T00:10:00+00:00\"}";
        SupabaseWalkRepository walks = new SupabaseWalkRepository(client);
        assertEquals(az.miyav.domain.walk.WalkSession.Status.ACTIVE,
                walks.start(session, az.miyav.domain.pet.PetSpecies.DOG, "Boncuk", "", 40.40931, 49.86715).status());
        assertTrue(transport.path.endsWith("/rest/v1/rpc/start_walk"));
        assertTrue(transport.requestBody.contains("\"p_lat\":40.40931"));

        transport.status = 400;
        transport.body = "{\"code\":\"P0001\",\"message\":\"NO_SESSION\"}";
        assertEquals("walk.no_session", assertThrows(az.miyav.common.error.ValidationException.class,
                () -> walks.updateLocation(session, 40.4, 49.8)).code());
        transport.body = "{\"code\":\"P0001\",\"message\":\"OUT_OF_AREA\"}";
        assertEquals("location.out_of_area", assertThrows(az.miyav.common.error.ValidationException.class,
                () -> walks.updateLocation(session, 40.4, 49.8)).code());
    }

    private static String serviceRoleJwt() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString("{\"role\":\"service_role\"}".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    private static final class RecordingTransport implements HttpTransport {
        private int status = 200;
        private String body = "";
        private String path = "";
        private String requestBody = "";
        private java.util.Map<String, String> headers = java.util.Map.of();

        @Override
        public HttpExchange send(String method, java.net.URI uri, java.util.Map<String, String> headers, String body) {
            this.path = uri.toString();
            this.headers = headers;
            this.requestBody = body == null ? "" : body;
            return new HttpExchange(status, this.body);
        }

        @Override
        public az.miyav.data.http.HttpBinaryExchange sendBinary(
                String method,
                java.net.URI uri,
                java.util.Map<String, String> headers,
                byte[] body,
                String contentType
        ) {
            this.path = uri.toString();
            this.headers = headers;
            this.requestBody = body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
            return new az.miyav.data.http.HttpBinaryExchange(status, this.body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
