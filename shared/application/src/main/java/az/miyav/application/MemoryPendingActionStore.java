package az.miyav.application;

import az.miyav.domain.auth.PendingAction;
import az.miyav.domain.auth.PendingActionStore;

import java.util.Optional;

public final class MemoryPendingActionStore implements PendingActionStore {
    private volatile PendingAction action;

    @Override
    public void save(PendingAction action) {
        this.action = action;
    }

    @Override
    public Optional<PendingAction> load() {
        return Optional.ofNullable(action);
    }

    @Override
    public void clear() {
        action = null;
    }
}
