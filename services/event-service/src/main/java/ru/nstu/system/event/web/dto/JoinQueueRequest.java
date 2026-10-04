package ru.nstu.system.event.web.dto;

/**
 * Body of {@code POST /api/events/{id}/queue} (spec "Вступление в очередь").
 *
 * <p>{@code name} is optional for a student/staff account (their profile name is
 * used when omitted) and required for a guest session. The 1..120 length rule and
 * the blank-name handling depend on the caller, so they live in the service layer
 * rather than in bean-validation annotations.</p>
 *
 * @param name requested entry name, may be {@code null}
 */
public record JoinQueueRequest(String name) {
}
