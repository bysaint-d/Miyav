package az.miyav.desktop;

import az.miyav.application.AdminService;
import az.miyav.application.AdoptionService;
import az.miyav.application.AuthService;
import az.miyav.application.LocationService;
import az.miyav.application.PetService;
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
import az.miyav.security.EncryptedFileSessionStore;
import az.miyav.security.RoleGuard;
import az.miyav.security.SessionStore;

import java.nio.file.Path;

public final class DesktopGraph {
    private final AuthService auth;
    private final PetService pets;
    private final UserService users;
    private final AdoptionService adoptions;
    private final AdminService admin;
    private final LocationService locations;
    private final SessionStore sessions;
    private final RoleGuard access;
    private MessageCatalog catalog;

    private DesktopGraph(
            AuthService auth,
            PetService pets,
            UserService users,
            AdoptionService adoptions,
            AdminService admin,
            LocationService locations,
            SessionStore sessions,
            RoleGuard access,
            MessageCatalog catalog
    ) {
        this.auth = auth;
        this.pets = pets;
        this.users = users;
        this.adoptions = adoptions;
        this.admin = admin;
        this.locations = locations;
        this.sessions = sessions;
        this.access = access;
        this.catalog = catalog;
    }

    public static DesktopGraph load() {
        MiyavBackend backend = MiyavBackend.connect(EnvironmentConfig.url(), EnvironmentConfig.publishableKey());
        SessionStore sessions = new EncryptedFileSessionStore(Path.of(System.getProperty("user.home"), ".miyav"));
        RoleGuard access = new RoleGuard();
        PetValidator petValidator = new PetValidator();
        SystemTimeSource time = new SystemTimeSource();
        return new DesktopGraph(
                new AuthService(backend.auth(), backend.users(), sessions, new EmailValidator(), new PasswordValidator(), time),
                new PetService(backend.pets(), backend.media(), petValidator, access),
                new UserService(backend.users(), new ProfileValidator()),
                new AdoptionService(backend.adoptions(), petValidator, access, time),
                new AdminService(backend.users(), backend.pets(), backend.adoptions(), backend.admin(), access),
                new LocationService(),
                sessions,
                access,
                new ResourceMessageCatalog(LocaleCode.AZ)
        );
    }

    public AuthService auth() {
        return auth;
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
}
