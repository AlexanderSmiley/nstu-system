package ru.nstu.system.auth.web;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.nstu.system.auth.web.dto.ApiErrorResponse;

/**
 * Translates service and validation failures into the uniform JSON error body
 * {@code {"error": "...", "message": "..."}} (tasks 5.1, 5.5).
 *
 * <p>Kept intentionally small: authentication and authorisation failures are
 * already expressed as {@link ApiException} with a precise status, and the Spring
 * Security entry point / access-denied handlers write their own bodies.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Некорректный запрос");
        return ResponseEntity.badRequest().body(new ApiErrorResponse("invalid_request", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("invalid_request", "Некорректный запрос"));
    }
}
