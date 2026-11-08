package az.miyav.data;

import az.miyav.data.http.OkHttpTransport;
import az.miyav.domain.repository.AdminRepository;
import az.miyav.domain.repository.AdoptionRepository;
import az.miyav.domain.repository.AuthRepository;
import az.miyav.domain.repository.MediaRepository;
import az.miyav.domain.repository.MessageRepository;
import az.miyav.domain.repository.NotificationRepository;
import az.miyav.domain.repository.PetRepository;
import az.miyav.domain.repository.UserRepository;
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
        backend.walks = new SupabaseWalkRepository(client);
        return backend;
    }

    public WalkRepository walks() {
        return walks;
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
