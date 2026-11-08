package az.miyav.common.error;

public final class PersistenceException extends AppException {
    public PersistenceException(String code) {
        super(code);
    }

    public PersistenceException(String code, Throwable cause) {
        super(code, cause);
    }
}
