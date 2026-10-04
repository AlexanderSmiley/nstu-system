package ru.nstu.system.auth.web;

import org.springframework.http.HttpStatus;

/**
 * Domain error carrying the HTTP status and a stable machine-readable code.
 *
 * <p>Translated into a JSON body by {@link ApiExceptionHandler}. The
 * {@code message} is user-facing Russian text; the {@code code} is a stable
 * identifier the SPA may branch on. Login failures deliberately reuse the same
 * code and message for an unknown username and a wrong password so the response
 * does not reveal which part was wrong (identity spec "Неверные учётные
 * данные").</p>
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

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException payloadTooLarge(String code, String message) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
