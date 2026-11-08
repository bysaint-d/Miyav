package az.miyav.android;

import android.app.Application;

import androidx.room.Room;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import az.miyav.android.data.AndroidSessionStore;
import az.miyav.android.data.MiyavDatabase;
import az.miyav.android.data.PendingAuthEmail;
import az.miyav.android.sync.OutboxSyncWorker;
import az.miyav.android.ui.MapSupport;
import az.miyav.android.ui.PhotoLoader;
import az.miyav.android.walk.WalkTracker;
import az.miyav.application.WalkingService;
import az.miyav.application.AdoptionService;
import az.miyav.application.AuthService;
import az.miyav.application.LocationService;
import az.miyav.application.NotificationService;
import az.miyav.application.PetService;
import az.miyav.application.UserService;
import az.miyav.common.time.SystemTimeSource;
import az.miyav.data.MiyavBackend;
import az.miyav.domain.validation.EmailValidator;
import az.miyav.domain.validation.PasswordValidator;
import az.miyav.domain.validation.PetValidator;
import az.miyav.domain.validation.ProfileValidator;
import az.miyav.security.RoleGuard;
import az.miyav.security.SessionStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MiyavApplication extends Application {
    private AuthService authService;
    private PetService petService;
    private UserService userService;
    private AdoptionService adoptionService;
    private NotificationService notificationService;
    private LocationService locationService;
    private SessionStore sessions;
    private MiyavDatabase database;
    private PendingAuthEmail pendingAuthEmail;
    private PhotoLoader photos;
    private WalkingService walkingService;
    private WalkTracker walkTracker;
    private final ExecutorService io = Executors.newFixedThreadPool(4);

    @Override
    public void onCreate() {
        super.onCreate();
        AppLocale.apply(this, AppLocale.current(this));
        MiyavBackend backend = MiyavBackend.connect(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY);
        sessions = new AndroidSessionStore(this);
        RoleGuard access = new RoleGuard();
        database = Room.databaseBuilder(this, MiyavDatabase.class, "miyav.db")
                .addMigrations(MiyavDatabase.MIGRATION_1_2)
                .build();
        PetValidator petValidator = new PetValidator();
        SystemTimeSource time = new SystemTimeSource();
        authService = new AuthService(backend.auth(), backend.users(), sessions, new EmailValidator(), new PasswordValidator(), time);
        petService = new PetService(backend.pets(), backend.media(), petValidator, access);
        userService = new UserService(backend.users(), new ProfileValidator(), backend.media());
        adoptionService = new AdoptionService(backend.adoptions(), petValidator, access, time);
        notificationService = new NotificationService(backend.notifications());
        locationService = new LocationService();
        walkingService = new WalkingService(backend.walks());
        walkTracker = new WalkTracker(this);
        pendingAuthEmail = new PendingAuthEmail(this);
        photos = new PhotoLoader(this);
        MapSupport.init(this);
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "miyav-outbox",
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(OutboxSyncWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build()
        );
    }

    public AuthService authService() {
        return authService;
    }

    public PetService petService() {
        return petService;
    }

    public UserService userService() {
        return userService;
    }

    public AdoptionService adoptionService() {
        return adoptionService;
    }

    public NotificationService notificationService() {
        return notificationService;
    }

    public LocationService locationService() {
        return locationService;
    }

    public SessionStore sessions() {
        return sessions;
    }

    public MiyavDatabase database() {
        return database;
    }

    public PendingAuthEmail pendingAuthEmail() {
        return pendingAuthEmail;
    }

    public PhotoLoader photos() {
        return photos;
    }

    public ExecutorService io() {
        return io;
    }

    private java.util.UUID pendingListing;

    /** Listing opened from a shared link; shown once the user is on the home screen. */
    public void setPendingListing(java.util.UUID listingId) {
        this.pendingListing = listingId;
    }

    public java.util.UUID takePendingListing() {
        java.util.UUID value = pendingListing;
        pendingListing = null;
        return value;
    }

    public WalkingService walkingService() {
        return walkingService;
    }

    public WalkTracker walkTracker() {
        return walkTracker;
    }

    public void onSignedOut() {
        walkTracker.signOut();
        photos.clear();
    }
}
