package az.miyav.domain.auth;

import java.util.Optional;

/** Keeps the action a guest tried before signing in, so the app can return to it afterwards. */
public interface PendingActionStore {
    void save(PendingAction action);

    Optional<PendingAction> load();

    void clear();
}
