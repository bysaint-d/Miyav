package az.miyav.data;

import az.miyav.data.http.OkHttpTransport;
import az.miyav.domain.repository.AdminInsightsRepository;
import az.miyav.domain.repository.AdminMessageRepository;
import az.miyav.domain.repository.PremiumRepository;
import az.miyav.domain.repository.AdminRepository;
import az.miyav.domain.repository.AdoptionRepository;
import az.miyav.domain.repository.AuthRepository;
import az.miyav.domain.repository.FavoriteRepository;
import az.miyav.domain.repository.LiveUpdates;
import az.miyav.domain.repository.MediaRepository;
import az.miyav.domain.repository.PushDeviceRepository;
import az.miyav.domain.repository.MessageRepository;
import az.miyav.domain.repository.NotificationRepository;
import az.miyav.domain.repository.PayoutRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.repository.PublicCatalogRepository;
import az.miyav.domain.repository.BroadcastRepository;
import az.miyav.domain.repository.OwnPetRepository;
import az.miyav.domain.repository.PlaceRepository;
import az.miyav.domain.repository.SafetyRepository;
import az.miyav.domain.repository.UserRepository;
import az.miyav.domain.repository.WalletRepository;
import az.miyav.domain.repository.WalkListingRepository;
import az.miyav.domain.repository.WalkRepository;

public final class MiyavBackend {
    private final AuthRepository auth;
    private final UserRepository users;
    private final PetRepository pets;
    private final AdoptionRepository adoptions;
    private final MessageRepository messages;
    private final NotificationRepository notifications;
    private final MediaRepository media;
    private final AdminRepository admin;
    private WalkRepository walks;
    private PublicCatalogRepository catalog;
    private FavoriteRepository favorites;
    private AdminInsightsRepository insights;
    private WalkListingRepository walkListings;
    private OwnPetRepository ownPets;
    private BroadcastRepository broadcasts;
    private SafetyRepository safety;
    private PlaceRepository places;
    private PushDeviceRepository pushDevices;
    private PremiumRepository premium;
    private AdminMessageRepository adminMessages;
    private PayoutRepository payouts;
    private WalletRepository wallets;
    private LiveUpdates live = LiveUpdates.OFF;
    private SupabaseClient client;

    public MiyavBackend(
            AuthRepository auth,
            UserRepository users,
            PetRepository pets,
            AdoptionRepository adoptions,
            MessageRepository messages,
            NotificationRepository notifications,
            MediaRepository media,
            AdminRepository admin
    ) {
        this.auth = auth;
        this.users = users;
        this.pets = pets;
        this.adoptions = adoptions;
        this.messages = messages;
        this.notifications = notifications;
        this.media = media;
        this.admin = admin;
    }

    public static MiyavBackend connect(String url, String publishableKey) {
        SupabaseConfig config = SupabaseConfig.parse(url, publishableKey);
        SupabaseClient client = new SupabaseClient(config, new OkHttpTransport());
        MiyavBackend backend = new MiyavBackend(
                new SupabaseAuthRepository(config, client),
                new SupabaseUserRepository(client),
                new SupabasePetRepository(client),
                new SupabaseAdoptionRepository(client),
                new SupabaseMessageRepository(client),
                new SupabaseNotificationRepository(client),
                new SupabaseMediaRepository(client),
                new SupabaseAdminRepository(client)
        );
        backend.walkListings = new SupabaseWalkListingRepository(client);
        backend.ownPets = new SupabaseOwnPetRepository(client);
        backend.broadcasts = new SupabaseBroadcastRepository(client);
        backend.safety = new SupabaseSafetyRepository(client);
        backend.places = new SupabasePlaceRepository(client);
        backend.pushDevices = new SupabasePushDeviceRepository(client);
        backend.premium = new SupabasePremiumRepository(client);
        backend.adminMessages = new SupabaseAdminMessageRepository(client);
        backend.payouts = new SupabasePayoutRepository(client);
        backend.wallets = new SupabaseWalletRepository(client);
        backend.live = new SupabaseRealtime(config);
        backend.client = client;
        return backend.withFeatures(
                new SupabaseWalkRepository(client),
                new SupabasePublicCatalogRepository(client),
                new SupabaseFavoriteRepository(client),
                new SupabaseAdminInsightsRepository(client));
    }

    /** Attaches the repositories added after the first release: walks, public catalog, favourites, admin views. */
    public MiyavBackend withFeatures(WalkRepository walks, PublicCatalogRepository catalog, FavoriteRepository favorites,
                                     AdminInsightsRepository insights) {
        this.walks = walks;
        this.catalog = catalog;
        this.favorites = favorites;
        this.insights = insights;
        return this;
    }

    /** Owners' walking listings and walkers' offers. */
    public WalkListingRepository walkListings() {
        return walkListings;
    }

    /** Phone push tokens. */
    public PushDeviceRepository pushDevices() {
        return pushDevices;
    }

    /** Paid premium placement requests and their review. */
    public PremiumRepository premium() {
        return premium;
    }

    /** Walkers' own payout details and accepted walker agreement. */
    public PayoutRepository payouts() {
        return payouts;
    }

    /** Opens the server connection in the background before the first screen needs it; call off the UI thread. */
    public void warmUp() {
        if (client != null) {
            client.warmUp();
        }
    }

    /** Walkers' MIYAV balance and withdrawal requests. */
    public WalletRepository wallets() {
        return wallets;
    }

    /** Replaces the payout details source, for offline previews. */
    public MiyavBackend withPayouts(PayoutRepository payouts) {
        this.payouts = payouts;
        return this;
    }

    /** Replaces the wallet source, for offline previews. */
    public MiyavBackend withWallets(WalletRepository wallets) {
        this.wallets = wallets;
        return this;
    }

    /** Administrators reading members' chats for moderation. */
    public AdminMessageRepository adminMessages() {
        return adminMessages;
    }

    /** Replaces the premium source, for offline previews. */
    public MiyavBackend withPremium(PremiumRepository premium) {
        this.premium = premium;
        return this;
    }

    /** Replaces the moderation chat source, for offline previews. */
    public MiyavBackend withAdminMessages(AdminMessageRepository adminMessages) {
        this.adminMessages = adminMessages;
        return this;
    }

    /** Realtime notices for chats and notifications; a no-op until connected. */
    public LiveUpdates live() {
        return live;
    }

    /** Replaces the walk requests source, for offline previews. */
    public MiyavBackend withWalkListings(WalkListingRepository walkListings) {
        this.walkListings = walkListings;
        return this;
    }

    /** Replaces the places source, for offline previews. */
    public MiyavBackend withPlaces(PlaceRepository places) {
        this.places = places;
        return this;
    }

    /** Vets and pet shops on the Vets tab. */
    public PlaceRepository places() {
        return places;
    }

    /** Reports and blocks between members. */
    public SafetyRepository safety() {
        return safety;
    }

    /** Admin push broadcasts. */
    public BroadcastRepository broadcasts() {
        return broadcasts;
    }

    /** The member's own pets ("Mənim heyvanlarım"). */
    public OwnPetRepository ownPets() {
        return ownPets;
    }

    /** Administrator-only monitoring views. */
    public AdminInsightsRepository insights() {
        return insights;
    }

    public WalkRepository walks() {
        return walks;
    }

    /** Anonymous, read-only access to published content for guests. */
    public PublicCatalogRepository catalog() {
        return catalog;
    }

    public FavoriteRepository favorites() {
        return favorites;
    }

    public AuthRepository auth() {
        return auth;
    }

    public UserRepository users() {
        return users;
    }

    public PetRepository pets() {
        return pets;
    }

    public AdoptionRepository adoptions() {
        return adoptions;
    }

    public MessageRepository messages() {
        return messages;
    }

    public NotificationRepository notifications() {
        return notifications;
    }

    public MediaRepository media() {
        return media;
    }

    public AdminRepository admin() {
        return admin;
    }
}
