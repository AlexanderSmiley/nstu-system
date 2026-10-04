package ru.nstu.system.event.error;

import org.springframework.http.HttpStatus;

/**
 * Domain error carrying the HTTP status and a stable machine-readable code.
 *
 * <p>Translated into the uniform JSON body {@code {"error": "...", "message": "..."}}
 * by {@link ru.nstu.system.event.web.ApiExceptionHandler}. The service layer
 * throws it; the web layer only renders it, so controllers stay free of
 * status-handling logic.</p>
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /** {@code 503} for a dependency that is temporarily unavailable (design.md D12). */
    public static ApiException serviceUnavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
