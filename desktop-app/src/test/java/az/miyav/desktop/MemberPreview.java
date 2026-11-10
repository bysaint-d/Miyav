package az.miyav.desktop;

import az.miyav.common.locale.LocaleCode;
import az.miyav.data.MiyavBackend;
import az.miyav.domain.adoption.AdoptionApplication;
import az.miyav.domain.adoption.ApplicationStatus;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.location.ListingLocation;
import az.miyav.domain.messaging.Conversation;
import az.miyav.domain.messaging.Message;
import az.miyav.domain.notification.Notification;
import az.miyav.domain.notification.NotificationType;
import az.miyav.domain.pet.Cat;
import az.miyav.domain.pet.Dog;
import az.miyav.domain.pet.ListingExtras;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.pet.PetGender;
import az.miyav.domain.pet.PetStatus;
import az.miyav.domain.pet.Vaccination;
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
import az.miyav.domain.user.PublicProfile;
import az.miyav.domain.user.User;
import az.miyav.domain.user.UserRole;
import az.miyav.security.MemorySessionStore;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Development tool, not part of the app: launches the desktop app on in-memory repositories with a signed-in
 * member, opens Discover and every member page, and saves a screenshot of each. Run with
 * {@code gradlew :desktop-app:memberPreview}.
 */
public final class MemberPreview {
    private static final Instant NOW = Instant.now();

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/member-preview");
        Files.createDirectories(out);
        MiyavDesktopApp.graphFactory = MemberPreview::graph;
        Thread capture = new Thread(() -> capture(out), "member-preview");
        capture.setDaemon(true);
        capture.start();
        Application.launch(MiyavDesktopApp.class);
    }

    private static void capture(Path out) {
        try {
            Thread.sleep(4000);
            List<ToggleButton> tabs = onFx(() -> ((HBox) scene().getRoot().lookup("#nav")).getChildren().stream()
                    .map(ToggleButton.class::cast).toList());
            int index = 0;
            for (ToggleButton tab : tabs) {
                onFx(() -> {
                    tab.fire();
                    return null;
                });
                Thread.sleep(1800);
                if (index == 0 || index == 1 || index == 2 || index == 4) {
                    onFx(() -> {
                        for (Node node : scene().getRoot().lookupAll(".list-view")) {
                            if (node instanceof ListView<?> list && !list.getItems().isEmpty() && list.isVisible()
                                    && !list.getStyleClass().contains("combo-box-popup")) {
                                list.getSelectionModel().select(0);
                            }
                        }
                        return null;
                    });
                    Thread.sleep(1500);
                }
                save(out.resolve(String.format("%02d-%s.png", ++index, tab.getText().replaceAll("[^A-Za-z0-9]+", "-"))));
            }
            onFx(() -> {
                tabs.get(0).fire();
                ((ToggleButton) scene().getRoot().lookup("#walkMode")).fire();
                return null;
            });
            Thread.sleep(1800);
            onFx(() -> {
                for (Node node : scene().getRoot().lookupAll(".list-view")) {
                    if (node instanceof ListView<?> list && list.getItems().size() == 2
                            && list.getItems().get(0) instanceof az.miyav.domain.walk.WalkListing) {
                        list.getSelectionModel().select(0);
                    }
                }
                return null;
            });
            Thread.sleep(1500);
            save(out.resolve("10-Gezdirme.png"));

            ToggleButton profile = tabs.get(tabs.size() - 1);
            Platform.runLater(() -> {
                graph.session().signOut();
                profile.fire();
            });
            Thread.sleep(2000);
            saveTop(out.resolve("11-Giris.png"));
            onFx(() -> {
                Window dialog = Window.getWindows().get(Window.getWindows().size() - 1);
                for (Node node : dialog.getScene().getRoot().lookupAll(".primary")) {
                    if (node instanceof javafx.scene.control.Button button) {
                        button.fire();
                        break;
                    }
                }
                return null;
            });
            Thread.sleep(1200);
            saveTop(out.resolve("12-Giris-forma.png"));
            Platform.runLater(() -> ((javafx.stage.Stage) Window.getWindows().get(Window.getWindows().size() - 1)).close());
        } catch (Exception exception) {
            exception.printStackTrace();
        } finally {
            Platform.exit();
        }
    }

    private static Scene scene() {
        return Window.getWindows().get(0).getScene();
    }

    private static void saveTop(Path file) throws Exception {
        write(onFx(() -> Window.getWindows().get(Window.getWindows().size() - 1).getScene().snapshot(null)), file);
    }

    private static void save(Path file) throws Exception {
        write(onFx(() -> scene().snapshot(null)), file);
    }

    private static void write(WritableImage image, Path file) throws Exception {
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

    private static DesktopGraph graph;

    private static DesktopGraph graph() {
        graph = build();
        return graph;
    }

    private static DesktopGraph build() {
        UUID me = UUID.randomUUID();
        UUID leyla = UUID.randomUUID();
        UUID murad = UUID.randomUUID();
        AuthSession session = new AuthSession(me, "elmir@miyav.az", "preview", "preview", NOW.plus(1, ChronoUnit.DAYS), UserRole.OWNER);
        MemorySessionStore sessions = new MemorySessionStore();
        sessions.save(session);

        ListingExtras baku = new ListingExtras(Vaccination.YES, new ListingLocation(40.4093, 49.8671, "Bakı"));
        Pet mine = new Dog(UUID.randomUUID(), me, "Boncuk", "Labrador", 26, PetGender.MALE, "Bakı",
                "Mehriban, uşaqlarla yaxşı yola gedir, peyvəndləri tamdır.", PetStatus.PUBLISHED, List.of("a/b/c.jpg")).withExtras(baku);
        Pet draft = new Cat(UUID.randomUUID(), me, "Pambıq", "", 5, PetGender.FEMALE, "Sumqayıt",
                "Sakit, evə öyrəşmiş pişik balası.", PetStatus.DRAFT, List.of());
        Pet other = new Dog(UUID.randomUUID(), murad, "Çaxır", "Qarışıq", 14, PetGender.MALE, "Bakı",
                "Enerjili, hər gün gəzməyi sevir.", PetStatus.PUBLISHED, List.of("a/b/d.jpg")).withExtras(baku);
        PetRepository pets = mock(PetRepository.class);
        when(pets.findOwned(any())).thenReturn(List.of(mine, draft));
        when(pets.findPublished(any(), any())).thenReturn(List.of(other, mine));
        when(pets.findById(eq(other.id()), any())).thenReturn(Optional.of(other));

        AdoptionRepository adoptions = mock(AdoptionRepository.class);
        when(adoptions.findByPet(any(), eq(mine.id()))).thenReturn(List.of(
                new AdoptionApplication(UUID.randomUUID(), mine.id(), leyla,
                        "Həyətli evimiz var, hər gün gəzdirə bilərəm. Əvvəl də itimiz olub.", ApplicationStatus.PENDING, NOW, NOW)));
        when(adoptions.findByApplicant(any())).thenReturn(List.of(
                new AdoptionApplication(UUID.randomUUID(), other.id(), me, "Çaxırı övladlığa götürmək istəyirəm.",
                        ApplicationStatus.PENDING, NOW.minus(1, ChronoUnit.DAYS), NOW)));

        UUID room = UUID.randomUUID();
        MessageRepository messages = mock(MessageRepository.class);
        when(messages.inbox(any())).thenReturn(List.of(
                new Conversation(room, leyla, "Leyla Quliyeva", NOW.minus(5, ChronoUnit.MINUTES), "Sabah saat 10-da uyğundur?", false, null, true),
                new Conversation(UUID.randomUUID(), murad, "Murad Əliyev", NOW.minus(2, ChronoUnit.HOURS), "", true, new BigDecimal("12"), false)));
        when(messages.messages(any(), eq(room), anyInt())).thenReturn(List.of(
                new Message(UUID.randomUUID(), room, me, "Salam! Boncuk hələ də yuva axtarır?", null, null, null, NOW.minus(30, ChronoUnit.MINUTES)),
                new Message(UUID.randomUUID(), room, leyla, "Bəli, görüşmək istərdim.", null, null, null, NOW.minus(20, ChronoUnit.MINUTES)),
                new Message(UUID.randomUUID(), room, leyla, "Sabah saat 10-da uyğundur?", null, null, null, NOW.minus(5, ChronoUnit.MINUTES))));

        NotificationRepository notifications = mock(NotificationRepository.class);
        when(notifications.list(any())).thenReturn(List.of(
                new Notification(UUID.randomUUID(), me, NotificationType.MESSAGE, "Yeni mesaj", "Leyla Quliyeva sizə mesaj yazdı.",
                        NOW.minus(5, ChronoUnit.MINUTES), null, room),
                new Notification(UUID.randomUUID(), me, NotificationType.ADOPTION, "Yeni övladlığa müraciət",
                        "«Boncuk» elanınıza yeni müraciət gəldi.", NOW.minus(3, ChronoUnit.HOURS), NOW)));

        UserRepository users = mock(UserRepository.class);
        when(users.findById(any())).thenReturn(Optional.of(new User(me, "elmir@miyav.az", "Elmir Həsənov", UserRole.OWNER, false,
                "Bakı", "Yasamal", "+994501234567", LocaleCode.AZ, null, "Heyvanları sevirəm.", true)));
        when(users.publicProfile(any(), any())).thenAnswer(call -> {
            UUID id = call.getArgument(1);
            String name = id.equals(leyla) ? "Leyla Quliyeva" : "Murad Əliyev";
            return Optional.of(new PublicProfile(id, name, null, "Bakı", "", true, "994501112233", NOW.minus(90, ChronoUnit.DAYS)));
        });

        MiyavBackend backend = new MiyavBackend(mock(AuthRepository.class), users, pets, adoptions, messages, notifications,
                mock(MediaRepository.class), mock(AdminRepository.class))
                .withFeatures(mock(WalkRepository.class), mock(PublicCatalogRepository.class), mock(FavoriteRepository.class),
                        mock(AdminInsightsRepository.class));
        az.miyav.domain.repository.WalkListingRepository walkListings = mock(az.miyav.domain.repository.WalkListingRepository.class);
        when(walkListings.findOpen(any(), any(), anyInt(), anyInt(), any())).thenReturn(List.of(
                new az.miyav.domain.walk.WalkListing(UUID.randomUUID(), murad,
                        new az.miyav.domain.walk.WalkListingDetails("Leo", az.miyav.domain.pet.PetSpecies.DOG,
                                NOW.plus(1, ChronoUnit.DAYS), 60, new BigDecimal("12"), "Bakı",
                                new ListingLocation(40.4093, 49.8671, "İmam Həsən məscidi, Binə"), "Sakit itdir, qayışla gəzir."),
                        az.miyav.domain.walk.WalkListingStatus.OPEN, null, NOW),
                new az.miyav.domain.walk.WalkListing(UUID.randomUUID(), leyla,
                        new az.miyav.domain.walk.WalkListingDetails("Zeytun", az.miyav.domain.pet.PetSpecies.DOG,
                                NOW.plus(2, ChronoUnit.DAYS), 90, new BigDecimal("15"), "Bakı", null, ""),
                        az.miyav.domain.walk.WalkListingStatus.OPEN, null, NOW)));
        az.miyav.domain.walk.WalkListing myRequest = new az.miyav.domain.walk.WalkListing(UUID.randomUUID(), me,
                new az.miyav.domain.walk.WalkListingDetails("Boncuk", az.miyav.domain.pet.PetSpecies.DOG,
                        NOW.plus(1, ChronoUnit.DAYS), 60, new BigDecimal("10"), "Bakı", null, "Qayışa öyrəşib."),
                az.miyav.domain.walk.WalkListingStatus.OPEN, null, NOW);
        az.miyav.domain.walk.WalkListing walked = new az.miyav.domain.walk.WalkListing(UUID.randomUUID(), leyla,
                new az.miyav.domain.walk.WalkListingDetails("Zeytun", az.miyav.domain.pet.PetSpecies.DOG,
                        NOW.minus(2, ChronoUnit.DAYS), 120, new BigDecimal("15"), "Bakı", null, ""),
                az.miyav.domain.walk.WalkListingStatus.COMPLETED, me, new BigDecimal("15"), NOW).withWalkerRating(5, "Çox diqqətli idi");
        when(walkListings.findMine(any(), any())).thenReturn(List.of(myRequest, walked));
        when(walkListings.offersForListings(any(), any())).thenReturn(List.of(new az.miyav.domain.walk.WalkOffer(UUID.randomUUID(),
                myRequest.id(), murad, "Hər gün parkda gəzirəm.", az.miyav.domain.walk.WalkOffer.Status.PENDING, new BigDecimal("12"), NOW)));
        when(walkListings.offersFor(any(), eq(myRequest.id()))).thenReturn(List.of(new az.miyav.domain.walk.WalkOffer(UUID.randomUUID(),
                myRequest.id(), murad, "Hər gün parkda gəzirəm.", az.miyav.domain.walk.WalkOffer.Status.PENDING, new BigDecimal("12"), NOW)));
        when(walkListings.walkerRatings(any(), any())).thenAnswer(call -> List.of(
                new az.miyav.domain.walk.WalkerRating(murad, new BigDecimal("4.8"), 12),
                new az.miyav.domain.walk.WalkerRating(me, new BigDecimal("5.0"), 1)));
        backend.withWalkListings(walkListings);

        az.miyav.domain.repository.WalletRepository wallets = mock(az.miyav.domain.repository.WalletRepository.class);
        when(wallets.wallet(any())).thenReturn(new az.miyav.domain.wallet.Wallet(new BigDecimal("80"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("40.56"), new BigDecimal("39.44"), 3, BigDecimal.TEN, new BigDecimal("5")));
        when(wallets.mine(any())).thenReturn(List.of(new az.miyav.domain.wallet.Withdrawal(UUID.randomUUID(), 42, me,
                new BigDecimal("40.56"), BigDecimal.TEN, new BigDecimal("4.06"), new BigDecimal("36.50"), "Elmir Həsənov",
                "Kapital Bank", "AZ21NABZ00000000137010001944", az.miyav.domain.wallet.Withdrawal.Status.PAID, "KB-TRX-778812", "",
                NOW.minus(1, ChronoUnit.DAYS), NOW.minus(2, ChronoUnit.DAYS))));
        backend.withWallets(wallets);
        az.miyav.domain.repository.PayoutRepository payouts = mock(az.miyav.domain.repository.PayoutRepository.class);
        when(payouts.mine(any())).thenReturn(Optional.of(new az.miyav.domain.payout.PayoutDetails("Elmir Həsənov", "Kapital Bank",
                "AZ21NABZ00000000137010001944", az.miyav.domain.payout.PayoutDetails.AGREEMENT_VERSION, NOW.minus(3, ChronoUnit.DAYS))));
        backend.withPayouts(payouts);
        return DesktopGraph.create(backend, sessions, Path.of(System.getProperty("java.io.tmpdir"), "miyav-member-preview"));
    }
}
