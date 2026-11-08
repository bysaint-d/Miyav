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
    void publicCatalogNeverSendsAUserToken() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        transport.body = "[]";
        SupabasePublicCatalogRepository catalog = new SupabasePublicCatalogRepository(new SupabaseClient(config, transport));

        catalog.published(az.miyav.domain.pet.PetQuery.firstPage());
        assertEquals("Bearer sb_publishable_test", transport.headers.get("Authorization"));
        assertTrue(transport.path.contains("status=eq.active"));

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        catalog.publishedListings(java.util.List.of(first, second));
        assertEquals("Bearer sb_publishable_test", transport.headers.get("Authorization"));
        assertTrue(transport.path.contains("status=eq.active&id=in.(" + first + "," + second + ")"));

        transport.body = "[]";
        catalog.publicProfile(first);
        assertTrue(transport.path.endsWith("/rest/v1/rpc/public_profile"));
        assertEquals("Bearer sb_publishable_test", transport.headers.get("Authorization"));
    }

    @Test
    void googlePkceSendsTheChallengeAndExchangesTheCode() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        SupabaseAuthRepository auth = new SupabaseAuthRepository(config, new SupabaseClient(config, transport));

        String url = auth.googleAuthorizeUri("az.miyav.native://auth", "challenge-1").toString();
        assertTrue(url.contains("code_challenge=challenge-1&code_challenge_method=s256"));

        UUID userId = UUID.randomUUID();
        transport.body = "{\"access_token\":\"t\",\"refresh_token\":\"r\",\"expires_in\":3600,\"user\":{\"id\":\"" + userId + "\",\"email\":\"g@example.com\"}}";
        assertEquals(userId, auth.exchangeCode("code-1", "verifier-1").userId());
        assertTrue(transport.path.endsWith("/auth/v1/token?grant_type=pkce"));
        assertTrue(transport.requestBody.contains("\"auth_code\":\"code-1\"") && transport.requestBody.contains("\"code_verifier\":\"verifier-1\""));

        transport.status = 400;
        transport.body = "{\"error_code\":\"flow_state_not_found\"}";
        PersistenceException rejected = assertThrows(PersistenceException.class, () -> auth.exchangeCode("forged", "verifier-1"));
        assertEquals("auth.link_invalid", rejected.code());
    }

    @Test
    void favouriteMergeUsesTheServerFunctionWithTheUserToken() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        transport.body = "[{\"added\":2,\"already_saved\":1,\"unavailable\":1}]";
        SupabaseFavoriteRepository favorites = new SupabaseFavoriteRepository(new SupabaseClient(config, transport));
        AuthSession session = new AuthSession(UUID.randomUUID(), "a@example.com", "user-token", "r",
                java.time.Instant.parse("2030-01-01T00:00:00Z"), null);

        var result = favorites.merge(session, java.util.List.of(UUID.randomUUID()));

        assertEquals(2, result.added());
        assertEquals(1, result.alreadySaved());
        assertEquals(1, result.unavailable());
        assertTrue(transport.path.endsWith("/rest/v1/rpc/merge_adoption_favorites"));
        assertEquals("Bearer user-token", transport.headers.get("Authorization"));
        assertThrows(az.miyav.common.error.AccessDeniedException.class, () -> favorites.list(null));
    }

    @Test
    void adminOverviewAndMapPointsMapIntoTheDomain() {
        SupabaseConfig config = SupabaseConfig.parse("https://example.supabase.co", "sb_publishable_test");
        RecordingTransport transport = new RecordingTransport();
        SupabaseAdminInsightsRepository insights = new SupabaseAdminInsightsRepository(new SupabaseClient(config, transport));
        AuthSession admin = new AuthSession(UUID.randomUUID(), "a@example.com", "admin-token", "r",
                java.time.Instant.parse("2030-01-01T00:00:00Z"), az.miyav.domain.user.UserRole.ADMIN);
        transport.body = "{\"users_total\":3,\"walks_total\":5,\"walks_live\":0,\"pets_total\":0,"
                + "\"requests_by_status\":{},\"listings_by_status\":{\"active\":1,\"adopted\":2},"
                + "\"walks_14d\":[{\"day\":\"2026-11-08\",\"count\":5}],\"registrations_14d\":[{\"day\":\"2026-11-08\",\"count\":0}],"
                + "\"cities\":[{\"city\":\"Bakı\",\"users\":3,\"listings\":1,\"helpers\":0}],"
                + "\"recent_walks\":[{\"id\":\"" + UUID.randomUUID() + "\",\"full_name\":\"Elmir\",\"pet_name\":\"Boncuk\","
                + "\"pet_species\":\"dog\",\"status\":\"ended\",\"started_at\":\"2026-11-08T00:18:29.146454+00:00\"}]}";

        var overview = insights.overview(admin);

        assertTrue(transport.path.endsWith("/rest/v1/rpc/admin_overview"));
        assertEquals("Bearer admin-token", transport.headers.get("Authorization"));
        assertEquals(3, overview.counts().users());
        assertEquals(5, overview.walks().get(0).count());
        assertEquals(2, overview.completedAdoptions());
        assertEquals(1, overview.activeListings());
        assertEquals("Bakı", overview.cities().get(0).city());
        assertEquals("Elmir", overview.recentWalks().get(0).title());

        transport.body = "[{\"kind\":\"helper\",\"id\":\"" + UUID.randomUUID() + "\",\"title\":\"Leyla\",\"subtitle\":\"Yasamal\","
                + "\"latitude\":40.41,\"longitude\":49.87,\"verified\":true},"
                + "{\"kind\":\"walk\",\"id\":\"" + UUID.randomUUID() + "\",\"title\":\"Elmir\",\"subtitle\":\"Boncuk\","
                + "\"latitude\":null,\"longitude\":null,\"verified\":false}]";
        var points = insights.mapPoints(admin);
        assertEquals(1, points.size(), "points without a shared position are dropped");
        assertEquals(az.miyav.domain.map.MapPointKind.WALK_HELPER, points.get(0).kind());

        transport.status = 403;
        transport.body = "{\"code\":\"P0001\",\"message\":\"FORBIDDEN\"}";
        assertThrows(az.miyav.common.error.AppException.class, () -> insights.users(admin));
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
