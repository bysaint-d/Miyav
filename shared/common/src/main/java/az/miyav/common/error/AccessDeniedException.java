package az.miyav.common.error;

public final class AccessDeniedException extends AppException {
    public AccessDeniedException(String code) {
        super(code);
    }
}
