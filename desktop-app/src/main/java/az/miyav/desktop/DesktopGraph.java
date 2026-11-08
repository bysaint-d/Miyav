package az.miyav.desktop;

import az.miyav.application.AdminInsightsService;
import az.miyav.application.AdminService;
import az.miyav.application.AdoptionService;
import az.miyav.application.AuthService;
import az.miyav.application.AuthStateManager;
import az.miyav.application.BroadcastService;
import az.miyav.application.DiscoveryService;
import az.miyav.application.FavoritesService;
import az.miyav.application.LocationService;
import az.miyav.application.MemoryPendingActionStore;
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
    private MessageCatalog catalog;

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
            MessageCatalog catalog
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
    }

    public static DesktopGraph load() {
        Path home = Path.of(System.getProperty("user.home"), ".miyav");
        return create(MiyavBackend.connect(EnvironmentConfig.url(), EnvironmentConfig.publishableKey()),
                new EncryptedFileSessionStore(home), home);
    }

    /** Composition root: wires every service from one backend and one session store. */
    public static DesktopGraph create(MiyavBackend backend, SessionStore sessions, Path home) {
        RoleGuard access = new RoleGuard();
        PetValidator petValidator = new PetValidator();
        SystemTimeSource time = new SystemTimeSource();
        AuthService auth = new AuthService(backend.auth(), backend.users(), sessions, new EmailValidator(), new PasswordValidator(), time);
        return new DesktopGraph(
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
                new ResourceMessageCatalog(LocaleCode.AZ)
        );
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

    public void selectLocale(LocaleCode locale) {
        catalog = new ResourceMessageCatalog(locale);
    }

    public String text(String key, Object... args) {
        String value = catalog.text(key);
        return args.length == 0 ? value : String.format(value, args);
    }
}
