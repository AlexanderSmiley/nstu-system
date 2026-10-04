package ru.nstu.system.student.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.nstu.system.student.error.ApiException;
import ru.nstu.system.student.web.dto.ApiErrorResponse;

/**
 * Translates service and validation failures into the uniform JSON body
 * {@code {"error": "...", "message": "..."}}.
 *
 * <p>Authentication and authorisation failures produced by the security filters
 * already carry their own terse JSON bodies and never reach this advice.</p>
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

    /**
     * The servlet multipart ceiling is a coarse guard; a file larger than it
     * never reaches the controller. Reuse the precise attachment code so the SPA
     * shows the same message as for the application-level 10 MiB limit
     * (change add-notes-module).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse("attachment_too_large",
                        "Размер файла не должен превышать 10 МБ"));
    }
}
