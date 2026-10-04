package ru.nstu.system.security;

/**
 * Thrown by {@link AccessTokenParser} when an access token cannot be trusted.
 *
 * <p>Reasons include a malformed token, a forged or unsupported signature, an
 * unexpected issuer and an expired token. Callers should translate this into an
 * HTTP 401 response.</p>
 */
public class InvalidTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
