package az.miyav.data;

import az.miyav.common.error.PersistenceException;

public final class NotReady {
    private NotReady() {
    }

    public static PersistenceException schema() {
        return new PersistenceException("schema.not_ready");
    }
}
