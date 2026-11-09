package az.miyav.android;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import az.miyav.android.data.AndroidFavoriteStore;
import az.miyav.android.data.AndroidPendingActionStore;
import az.miyav.android.data.AndroidSessionStore;
import az.miyav.android.data.MiyavDatabase;
import az.miyav.android.data.PendingAuthEmail;
import az.miyav.android.data.PendingGoogleSignIn;
import az.miyav.android.push.Push;
import az.miyav.application.PushService;
import az.miyav.domain.repository.LiveUpdates;
import az.miyav.android.sync.NotificationPollWorker;
import az.miyav.android.sync.OutboxSyncWorker;
import az.miyav.android.ui.MapSupport;
import az.miyav.android.ui.PhotoLoader;
import az.miyav.android.walk.WalkTracker;
import az.miyav.application.WalkingService;
import az.miyav.application.WalkListingService;
import az.miyav.application.AdoptionService;
import az.miyav.application.AuthService;
import az.miyav.application.AuthStateManager;
import az.miyav.application.DiscoveryService;
import az.miyav.application.FavoritesService;
import az.miyav.application.LocationService;
import az.miyav.application.MessagingService;
import az.miyav.application.MyPetsService;
import az.miyav.application.PlacesService;
import az.miyav.application.SafetyService;
import az.miyav.application.TelegramVerification;
import az.miyav.application.UnavailableTelegramVerification;
import az.miyav.application.NotificationService;
import az.miyav.application.PetService;
import az.miyav.application.UserService;
import az.miyav.common.time.SystemTimeSource;
import az.miyav.data.MiyavBackend;
import az.miyav.domain.validation.EmailValidator;
import az.miyav.domain.validation.PasswordValidator;
import az.miyav.domain.validation.PetValidator;
import az.miyav.domain.validation.ProfileValidator;
import az.miyav.domain.auth.AuthState;
import az.miyav.security.AccessPolicy;
import az.miyav.security.RoleGuard;
import az.miyav.security.SessionStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MiyavApplication extends Application {
    private AuthService authService;
    private AuthStateManager auth;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>(AuthState.GUEST);
    private DiscoveryService discovery;
    private FavoritesService favorites;
    private final TelegramVerification telegram = new UnavailableTelegramVerification();
    private PendingGoogleSignIn pendingGoogle;
    private volatile boolean favoritesOffered;
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
    private WalkListingService walkListings;
    private MessagingService messaging;
    private MyPetsService myPets;
    private SafetyService safety;
    private PlacesService places;
    private WalkTracker walkTracker;
    private PushService push;
    private LiveUpdates live;
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
        auth = new AuthStateManager(authService, new AndroidPendingActionStore(this), new AccessPolicy());
        auth.addListener(authState::postValue);
        auth.prime();
        discovery = new DiscoveryService(backend.catalog(), backend.pets(), backend.users(), backend.media(), petValidator);
        favorites = new FavoritesService(new AndroidFavoriteStore(this), backend.favorites());
        pendingGoogle = new PendingGoogleSignIn(this);
        petService = new PetService(backend.pets(), backend.media(), petValidator, access);
        userService = new UserService(backend.users(), new ProfileValidator(), backend.media());
        adoptionService = new AdoptionService(backend.adoptions(), petValidator, access, time);
        notificationService = new NotificationService(backend.notifications());
        locationService = new LocationService();
        walkingService = new WalkingService(backend.walks());
        walkListings = new WalkListingService(backend.walkListings(), time);
        messaging = new MessagingService(backend.messages());
        myPets = new MyPetsService(backend.ownPets());
        safety = new SafetyService(backend.safety());
        places = new PlacesService(backend.places(), time);
        push = new PushService(backend.pushDevices());
        live = backend.live();
        Push.init(this);
        walkTracker = new WalkTracker(this);
        pendingAuthEmail = new PendingAuthEmail(this);
        photos = new PhotoLoader(this);
        MapSupport.init(this);
        NotificationPollWorker.schedule(this);
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

    /** Central authentication state; screens ask it instead of inspecting sessions. */
    public AuthStateManager auth() {
        return auth;
    }

    /** Auth state for observers on the main thread. */
    public LiveData<AuthState> authState() {
        return authState;
    }

    public DiscoveryService discovery() {
        return discovery;
    }

    public FavoritesService favorites() {
        return favorites;
    }

    /** Nearest vets and pet shops. */
    public PlacesService places() {
        return places;
    }

    /** Reports and blocks; also filters blocked members out of lists. */
    public SafetyService safety() {
        return safety;
    }

    public TelegramVerification telegram() {
        return telegram;
    }

    public PendingGoogleSignIn pendingGoogle() {
        return pendingGoogle;
    }

    /** True once per sign-in, so the favourites sync offer is not repeated on every screen. */
    public synchronized boolean claimFavoritesOffer() {
        if (favoritesOffered) {
            return false;
        }
        favoritesOffered = true;
        return true;
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

    private String pendingTap;

    /** A tapped phone notification: a conversation id, or "" for the notification list. */
    public void setPendingTap(String target) {
        this.pendingTap = target;
    }

    public String takePendingTap() {
        String value = pendingTap;
        pendingTap = null;
        return value;
    }

    public java.util.UUID takePendingListing() {
        java.util.UUID value = pendingListing;
        pendingListing = null;
        return value;
    }

    public WalkingService walkingService() {
        return walkingService;
    }

    /** Paid walk requests posted by owners, separate from adoption listings. */
    public WalkListingService walkListings() {
        return walkListings;
    }

    /** Private chats between signed-in users (walk price negotiation, adoption questions). */
    public MessagingService messaging() {
        return messaging;
    }

    /** This phone's push token on the server. */
    public PushService push() {
        return push;
    }

    /** Realtime notices: open chats reload as soon as a message arrives. */
    public LiveUpdates live() {
        return live;
    }

    /** "Mənim heyvanlarım": the member's own pets. */
    public MyPetsService myPets() {
        return myPets;
    }

    public WalkTracker walkTracker() {
        return walkTracker;
    }

    public void onSignedOut() {
        az.miyav.domain.auth.AuthSession session = auth.viewer();
        if (session != null) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                io.execute(() -> Push.unregister(this, session));
            } else {
                Push.unregister(this, session);
            }
        }
        walkTracker.signOut();
        photos.clear();
        safety.clear();
        favoritesOffered = false;
    }
}
