package ru.nstu.system.auth.web.dto;

/**
 * Uniform JSON error body of the authentication API.
 *
 * @param error   stable machine-readable code, e.g. {@code invalid_credentials}
 * @param message user-facing Russian message
 */
public record ApiErrorResponse(String error, String message) {
}
