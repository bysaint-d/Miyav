package az.miyav.desktop;

import az.miyav.common.locale.LocaleCode;
import az.miyav.data.MiyavBackend;
import az.miyav.domain.admin.ActivityItem;
import az.miyav.domain.admin.CityActivity;
import az.miyav.domain.admin.DailyCount;
import az.miyav.domain.admin.PlatformOverview;
import az.miyav.domain.admin.UserSummary;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.GeoPoint;
import az.miyav.domain.map.MapPoint;
import az.miyav.domain.map.MapPointKind;
import az.miyav.domain.moderation.Report;
import az.miyav.domain.pet.Cat;
import az.miyav.domain.pet.Dog;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetProfile;
import az.miyav.domain.pet.PetSpecies;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.repository.AdminInsightsRepository;
import az.miyav.domain.repository.AdminRepository;
import az.miyav.domain.repository.AdoptionRepository;
import az.miyav.domain.repository.AuthRepository;
import az.miyav.domain.repository.FavoriteRepository;
import az.miyav.domain.repository.MediaRepository;
import az.miyav.domain.repository.MessageRepository;
import az.miyav.domain.repository.NotificationRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.repository.PublicCatalogRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.repository.WalkRepository;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import az.miyav.domain.walk.CareService;
import az.miyav.domain.walk.WalkActivity;
import az.miyav.domain.walk.WalkRequest;
import az.miyav.domain.walk.WalkSession;
import az.miyav.security.MemorySessionStore;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Development tool, not part of the app: launches the real desktop app on in-memory repositories with a
 * signed-in administrator, opens every console page and saves a screenshot. Run with
 * {@code gradlew :desktop-app:consolePreview}.
 */
public final class ConsolePreview {
    private static final Instant NOW = Instant.now();

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/console-preview");
        Files.createDirectories(out);
        MiyavDesktopApp.graphFactory = ConsolePreview::graph;
        Thread capture = new Thread(() -> capture(out), "console-preview");
        capture.setDaemon(true);
        capture.start();
        Application.launch(MiyavDesktopApp.class);
    }

    private static void capture(Path out) {
        try {
            Thread.sleep(4000);
            List<ToggleButton> nav = onFx(() -> {
                List<ToggleButton> buttons = new ArrayList<>();
                for (Node node : scene().getRoot().lookupAll(".nav-item")) {
                    buttons.add((ToggleButton) node);
                }
                return buttons;
            });
            int index = 0;
            for (ToggleButton button : nav) {
                onFx(() -> {
                    button.fire();
                    return null;
                });
                Thread.sleep(button.getText().contains("NearMe") ? 6000 : 1800);
                save(out.resolve(String.format("%02d-%s.png", ++index, button.getText().replaceAll("[^A-Za-z0-9]+", "-"))));
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        } finally {
            Platform.exit();
        }
    }

    private static Scene scene() {
        return Window.getWindows().get(0).getScene();
    }

    private static void save(Path file) throws Exception {
        WritableImage image = onFx(() -> scene().snapshot(null));
        PixelReader pixels = image.getPixelReader();
        BufferedImage buffered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", new File(file.toString()));
        System.out.println("saved " + file);
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });
        return result.get();
    }

    private static DesktopGraph graph() {
        UUID adminId = UUID.randomUUID();
        AuthSession admin = new AuthSession(adminId, "admin@miyav.az", "preview", "preview", NOW.plus(1, ChronoUnit.DAYS), UserRole.ADMIN);
        MemorySessionStore sessions = new MemorySessionStore();
        sessions.save(admin);

        List<UserSummary> members = List.of(
                member("Elmir Həsənov", "Bakı", "Yasamal", UserRole.OWNER, false, 40, 2, 5, 1),
                member("Leyla Quliyeva", "Bakı", "Nəsimi", UserRole.SITTER, false, 120, 1, 14, 0),
                member("Murad Əliyev", "Sumqayıt", "", UserRole.OWNER, false, 3, 1, 0, 2),
                member("Aysel Məmmədova", "Bakı", "Xətai", UserRole.OWNER, true, 200, 0, 1, 0));
        UUID elmir = members.get(0).id();
        UUID leyla = members.get(1).id();
        UUID murad = members.get(2).id();

        AdminInsightsRepository insights = mock(AdminInsightsRepository.class);
        when(insights.users(any())).thenReturn(members);
        when(insights.overview(any())).thenReturn(overview(members));
        when(insights.walkRequests(any(), any())).thenReturn(List.of(
                request(elmir, "Elmir Həsənov", leyla, "Leyla Quliyeva", "Boncuk", "dog", NOW.minus(20, ChronoUnit.MINUTES), "accepted"),
                request(murad, "Murad Əliyev", leyla, "Leyla Quliyeva", "Mestan", "dog", NOW.plus(1, ChronoUnit.DAYS), "pending"),
                request(elmir, "Elmir Həsənov", leyla, "Leyla Quliyeva", "Boncuk", "dog", NOW.minus(3, ChronoUnit.DAYS), "completed")));
        WalkActivity live = new WalkActivity(UUID.randomUUID(), elmir, "Elmir Həsənov", "Boncuk", PetSpecies.DOG,
                WalkSession.Status.ACTIVE, "Parkda gəzirik", NOW.minus(15, ChronoUnit.MINUTES), null, new GeoPoint(40.38, 49.83));
        when(insights.walkActivity(any())).thenReturn(List.of(live,
                new WalkActivity(UUID.randomUUID(), leyla, "Leyla Quliyeva", "Toplan", PetSpecies.DOG, WalkSession.Status.ENDED, "",
                        NOW.minus(1, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS).plus(45, ChronoUnit.MINUTES), null)));
        when(insights.mapPoints(any())).thenReturn(List.of(
                MapPoint.of(MapPointKind.LIVE_WALK, live.id(), "Elmir Həsənov", "Boncuk · dog", new GeoPoint(40.38, 49.83), false),
                MapPoint.of(MapPointKind.WALK_HELPER, leyla, "Leyla Quliyeva", "Nəsimi · Bakı", new GeoPoint(40.40, 49.85), true),
                MapPoint.of(MapPointKind.ADOPTION, UUID.randomUUID(), "Mestan", "cat · Bakı", new GeoPoint(40.409, 49.867), false)));
        when(insights.pets(any())).thenReturn(List.of(
                new PetProfile(UUID.randomUUID(), elmir, "Elmir Həsənov", "Boncuk", PetSpecies.DOG, "Labrador",
                        new BigDecimal("3"), new BigDecimal("24.5"), null, NOW),
                new PetProfile(UUID.randomUUID(), murad, "Murad Əliyev", "Mestan", PetSpecies.CAT, "", new BigDecimal("1"), null, null, NOW)));

        Dog dog = new Dog(UUID.randomUUID(), murad, "Çaxır", "Qarışıq", 14, PetGender.MALE, "Bakı",
                "Mehriban və enerjili, uşaqlarla yaxşı yola gedir.", PetStatus.PUBLISHED, List.of("a/b/c.jpg"));
        Cat cat = new Cat(UUID.randomUUID(), elmir, "Pambıq", "", 6, PetGender.FEMALE, "Bakı",
                "Sakit, evə öyrəşmiş pişik balası.", PetStatus.ADOPTED, List.of("a/b/d.jpg"));
        PetRepository pets = mock(PetRepository.class);
        when(pets.findForAdmin(any())).thenReturn(List.of(dog, cat));
        AdoptionRepository adoptions = mock(AdoptionRepository.class);
        when(adoptions.findForAdmin(any())).thenReturn(List.of(new AdoptionApplication(UUID.randomUUID(), dog.id(), elmir,
                "Həyətli evimiz var, hər gün gəzdirə bilərəm.", ApplicationStatus.PENDING, NOW, NOW)));
        AdminRepository adminActions = mock(AdminRepository.class);
        when(adminActions.listReports(any())).thenReturn(List.of(new Report(UUID.randomUUID(), elmir, murad,
                "Gəzinti vaxtında gəlmədi və cavab vermir.", false, NOW.minus(2, ChronoUnit.HOURS))));
        UserRepository users = mock(UserRepository.class);
        when(users.findById(any())).thenReturn(java.util.Optional.of(
                new User(adminId, "admin@miyav.az", "MIYAV Admin", UserRole.ADMIN, false, "Bakı", "", "", LocaleCode.AZ)));
        MediaRepository media = mock(MediaRepository.class);

        MiyavBackend backend = new MiyavBackend(mock(AuthRepository.class), users, pets, adoptions,
                mock(MessageRepository.class), mock(NotificationRepository.class), media, adminActions)
                .withFeatures(mock(WalkRepository.class), mock(PublicCatalogRepository.class), mock(FavoriteRepository.class), insights);
        return DesktopGraph.create(backend, sessions, Path.of(System.getProperty("java.io.tmpdir"), "miyav-preview"));
    }

    private static UserSummary member(String name, String city, String district, UserRole role, boolean suspended,
                                      int daysAgo, int pets, int walks, int listings) {
        Instant joined = NOW.minus(daysAgo, ChronoUnit.DAYS);
        return new UserSummary(UUID.randomUUID(), name, city, district, role, suspended, null, joined, pets, listings, walks, 1,
                NOW.minus(Math.min(daysAgo, 2), ChronoUnit.DAYS));
    }

    private static WalkRequest request(UUID owner, String ownerName, UUID walker, String walkerName, String pet, String type,
                                       Instant start, String status) {
        return new WalkRequest(UUID.randomUUID(), new WalkRequest.Participant(owner, ownerName),
                new WalkRequest.Participant(walker, walkerName), pet, type, CareService.WALKING, start,
                start.plus(1, ChronoUnit.HOURS), status, new BigDecimal("12"), "hour", "Bakı", "Nəsimi",
                "Qapının kodu mesajda.", start.minus(1, ChronoUnit.DAYS));
    }

    private static PlatformOverview overview(List<UserSummary> members) {
        List<DailyCount> registrations = new ArrayList<>();
        List<DailyCount> walks = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int[] joined = {0, 1, 0, 0, 2, 1, 0, 0, 1, 0, 3, 1, 0, 2};
        int[] walked = {1, 2, 2, 0, 3, 4, 2, 1, 5, 3, 4, 6, 2, 5};
        for (int i = 0; i < 14; i++) {
            registrations.add(new DailyCount(today.minusDays(13 - i), joined[i]));
            walks.add(new DailyCount(today.minusDays(13 - i), walked[i]));
        }
        Map<String, Integer> requests = new LinkedHashMap<>();
        requests.put("pending", 1);
        requests.put("accepted", 1);
        requests.put("completed", 1);
        Map<String, Integer> listings = new LinkedHashMap<>();
        listings.put("active", 1);
        listings.put("adopted", 1);
        List<ActivityItem> recentUsers = new ArrayList<>();
        for (UserSummary member : members) {
            recentUsers.add(new ActivityItem(ActivityItem.Kind.REGISTRATION, member.id(), member.fullName(), member.city(),
                    member.role().databaseValue(), member.joinedAt()));
        }
        return new PlatformOverview(
                new PlatformOverview.Counts(members.size(), 1, 1, 3, 2, 1, 1, 2, 41, 1, 1, 1, 2, 9, 12, 3),
                requests, listings, registrations, walks,
                List.of(new CityActivity("Bakı", 3, 1, 1), new CityActivity("Sumqayıt", 1, 0, 0)),
                recentUsers,
                List.of(new ActivityItem(ActivityItem.Kind.WALK, UUID.randomUUID(), "Elmir Həsənov", "Boncuk · dog", "active",
                        NOW.minus(15, ChronoUnit.MINUTES))),
                List.of(new ActivityItem(ActivityItem.Kind.ADOPTION, UUID.randomUUID(), "Çaxır", "dog · Bakı", "active",
                        NOW.minus(5, ChronoUnit.HOURS))));
    }
}
