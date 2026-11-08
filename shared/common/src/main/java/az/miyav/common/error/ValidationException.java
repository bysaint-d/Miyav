package az.miyav.common.error;

public final class ValidationException extends AppException {
    public ValidationException(String code) {
        super(code);
    }
}
