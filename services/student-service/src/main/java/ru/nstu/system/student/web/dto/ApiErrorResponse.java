package ru.nstu.system.student.web.dto;

/**
 * Uniform JSON error body of the student API.
 *
 * @param error   stable machine-readable code, e.g. {@code profile_not_found}
 * @param message user-facing Russian message
 */
public record ApiErrorResponse(String error, String message) {
}
