package az.miyav.desktop;

import az.miyav.application.AdminInsightsService;
import az.miyav.application.AdminMessagesService;
import az.miyav.application.PayoutService;
import az.miyav.application.PremiumService;
import az.miyav.application.WalletService;
import az.miyav.application.AdminService;
import az.miyav.application.AdoptionService;
import az.miyav.application.AuthService;
import az.miyav.application.AuthStateManager;
import az.miyav.application.BroadcastService;
import az.miyav.application.DiscoveryService;
import az.miyav.application.FavoritesService;
import az.miyav.application.LocationService;
import az.miyav.application.MemoryPendingActionStore;
import az.miyav.application.MessagingService;
import az.miyav.application.NotificationService;
import az.miyav.application.PlacesService;
import az.miyav.application.WalkListingService;
import az.miyav.domain.repository.LiveUpdates;
import az.miyav.application.PetService;
import az.miyav.application.TelegramVerification;
import az.miyav.application.UnavailableTelegramVerification;
import az.miyav.application.UserService;
import az.miyav.common.locale.LocaleCode;
import az.miyav.common.locale.MessageCatalog;
import az.miyav.common.locale.ResourceMessageCatalog;
import az.miyav.common.time.SystemTimeSource;
import az.miyav.data.EnvironmentConfig;
import az.miyav.data.MiyavBackend;
import az.miyav.domain.validation.EmailValidator;
import az.miyav.domain.validation.PasswordValidator;
import az.miyav.domain.validation.PetValidator;
import az.miyav.domain.validation.ProfileValidator;
import az.miyav.security.AccessPolicy;
import az.miyav.security.EncryptedFileSessionStore;
import az.miyav.security.RoleGuard;
import az.miyav.security.SessionStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DesktopGraph {
    private final AuthService auth;
    private final AuthStateManager session;
    private final DiscoveryService discovery;
    private final FavoritesService favorites;
    private final TelegramVerification telegram;
    private final PetService pets;
    private final UserService users;
    private final AdoptionService adoptions;
    private final AdminService admin;
    private final AdminInsightsService insights;
    private final BroadcastService broadcasts;
    private final LocationService locations;
    private final SessionStore sessions;
    private final RoleGuard access;
    private final Path home;
    private MessageCatalog catalog;
    private MessagingService messaging;
    private NotificationService notifications;
    private LiveUpdates live = LiveUpdates.OFF;
    private PlacesService places;
    private WalkListingService walkListings;
    private AdminMessagesService adminMessages;
    private PremiumService premium;
    private WalletService wallets;
    private PayoutService payouts;

    private DesktopGraph(
            AuthService auth,
            AuthStateManager session,
            DiscoveryService discovery,
            FavoritesService favorites,
            PetService pets,
            UserService users,
            AdoptionService adoptions,
            AdminService admin,
            AdminInsightsService insights,
            BroadcastService broadcasts,
            LocationService locations,
            SessionStore sessions,
            RoleGuard access,
            MessageCatalog catalog,
            Path home
    ) {
        this.auth = auth;
        this.session = session;
        this.discovery = discovery;
        this.favorites = favorites;
        this.telegram = new UnavailableTelegramVerification();
        this.pets = pets;
        this.users = users;
        this.adoptions = adoptions;
        this.admin = admin;
        this.insights = insights;
        this.broadcasts = broadcasts;
        this.locations = locations;
        this.sessions = sessions;
        this.access = access;
        this.catalog = catalog;
        this.home = home;
    }

    public static DesktopGraph load() {
        Path home = Path.of(System.getProperty("user.home"), ".miyav");
        MiyavBackend backend = MiyavBackend.connect(EnvironmentConfig.url(), EnvironmentConfig.publishableKey());
        Thread warm = new Thread(backend::warmUp, "miyav-warm-up");
        warm.setDaemon(true);
        warm.start();
        return create(backend, new EncryptedFileSessionStore(home), home);
    }

    /** Composition root: wires every service from one backend and one session store. */
    public static DesktopGraph create(MiyavBackend backend, SessionStore sessions, Path home) {
        RoleGuard access = new RoleGuard();
        PetValidator petValidator = new PetValidator();
        SystemTimeSource time = new SystemTimeSource();
        AuthService auth = new AuthService(backend.auth(), backend.users(), sessions, new EmailValidator(), new PasswordValidator(), time);
        DesktopGraph graph = new DesktopGraph(
                auth,
                new AuthStateManager(auth, new MemoryPendingActionStore(), new AccessPolicy()),
                new DiscoveryService(backend.catalog(), backend.pets(), backend.users(), backend.media(), petValidator),
                new FavoritesService(new FileFavoriteStore(home), backend.favorites()),
                new PetService(backend.pets(), backend.media(), petValidator, access),
                new UserService(backend.users(), new ProfileValidator(), backend.media()),
                new AdoptionService(backend.adoptions(), petValidator, access, time),
                new AdminService(backend.users(), backend.pets(), backend.adoptions(), backend.admin(), access),
                new AdminInsightsService(backend.insights(), access),
                new BroadcastService(backend.broadcasts(), access),
                new LocationService(),
                sessions,
                access,
                new ResourceMessageCatalog(savedLocale(home)),
                home
        );
        graph.messaging = new MessagingService(backend.messages());
        graph.notifications = new NotificationService(backend.notifications());
        graph.live = backend.live() == null ? LiveUpdates.OFF : backend.live();
        graph.places = new PlacesService(backend.places(), time);
        graph.walkListings = new WalkListingService(backend.walkListings(), time);
        graph.adminMessages = backend.adminMessages() == null ? null : new AdminMessagesService(backend.adminMessages(), access);
        graph.premium = backend.premium() == null ? null : new PremiumService(backend.premium(), access);
        graph.wallets = backend.wallets() == null ? null : new WalletService(backend.wallets(), access);
        graph.payouts = backend.payouts() == null ? null : new PayoutService(backend.payouts());
        return graph;
    }

    /** Walkers' balances and withdrawal requests; administrators pay them and record the bank reference. */
    public WalletService wallets() {
        return wallets;
    }

    /** The signed-in walker's bank details and accepted walker agreement. */
    public PayoutService payouts() {
        return payouts;
    }

    /** Moderators reading members' chats; every opened conversation is audited on the server. */
    public AdminMessagesService adminMessages() {
        return adminMessages;
    }

    /** Premium listing requests: receipts to check, approve or reject. */
    public PremiumService premium() {
        return premium;
    }

    /** Paid walk requests ("Gəzdirmə"): browse, offer a price, chat with the owner. */
    public WalkListingService walkListings() {
        return walkListings;
    }

    /** Vets and pet shops; admins maintain the list from the console. */
    public PlacesService places() {
        return places;
    }

    /** Private chats: walk negotiations and questions to adoption listing owners. */
    public MessagingService messaging() {
        return messaging;
    }

    public NotificationService notifications() {
        return notifications;
    }

    /** Realtime notices so open chats and the notification badge update without waiting. */
    public LiveUpdates live() {
        return live;
    }

    private static LocaleCode savedLocale(Path home) {
        try {
            return LocaleCode.fromTag(Files.readString(home.resolve("locale")).trim());
        } catch (IOException | RuntimeException exception) {
            return LocaleCode.AZ;
        }
    }

    public AuthService auth() {
        return auth;
    }

    /** Central authentication state shared by every desktop screen. */
    public AuthStateManager session() {
        return session;
    }

    public DiscoveryService discovery() {
        return discovery;
    }

    public FavoritesService favorites() {
        return favorites;
    }

    public TelegramVerification telegram() {
        return telegram;
    }

    public PetService pets() {
        return pets;
    }

    public UserService users() {
        return users;
    }

    public AdoptionService adoptions() {
        return adoptions;
    }

    public AdminService admin() {
        return admin;
    }

    /** Read-only monitoring for the admin console. */
    public AdminInsightsService insights() {
        return insights;
    }

    /** Push notifications from the admin console. */
    public BroadcastService broadcasts() {
        return broadcasts;
    }

    public LocationService locations() {
        return locations;
    }

    public SessionStore sessions() {
        return sessions;
    }

    public RoleGuard access() {
        return access;
    }

    public MessageCatalog catalog() {
        return catalog;
    }

    /**
     * Switches the UI language, remembers it for the next start and, when signed in, moves the profile to
     * it so server notifications arrive in the same language.
     */
    public void selectLocale(LocaleCode locale) {
        catalog = new ResourceMessageCatalog(locale);
        if (home != null) {
            try {
                Files.createDirectories(home);
                Files.writeString(home.resolve("locale"), locale.tag());
            } catch (IOException | RuntimeException ignored) {
                // The language still applies for this run.
            }
        }
        syncProfileLocale();
    }

    /** Called after sign-in too; a failure leaves the profile language unchanged. */
    public void syncProfileLocale() {
        LocaleCode locale = catalog.locale();
        session.session().ifPresent(current -> {
            Thread worker = new Thread(() -> {
                try {
                    users.syncLocale(current, locale);
                } catch (RuntimeException ignored) {
                    // Retried on the next sign-in or language change.
                }
            }, "miyav-locale");
            worker.setDaemon(true);
            worker.start();
        });
    }

    public String text(String key, Object... args) {
        String value = catalog.text(key);
        return args.length == 0 ? value : String.format(value, args);
    }
}
