package ru.nstu.system.event.web.dto;

/**
 * Uniform JSON error body of the event API.
 *
 * @param error   stable machine-readable code, e.g. {@code event_not_found}
 * @param message user-facing Russian message
 */
public record ApiErrorResponse(String error, String message) {
}
