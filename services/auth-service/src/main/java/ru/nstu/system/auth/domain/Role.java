package ru.nstu.system.auth.domain;

/**
 * Persisted account roles.
 *
 * <p>{@code GUEST} is intentionally absent: guest access is an anonymous session
 * (see design.md D11) and is never stored as an {@code account} row.</p>
 */
public enum Role {
    ADMIN,
    STAFF,
    STUDENT
}
