package io.github.trustrag.core.exception;

public class TrustRagException extends RuntimeException {

    public TrustRagException(String message) {
        super(message);
    }

    public TrustRagException(String message, Throwable cause) {
        super(message, cause);
    }
}
