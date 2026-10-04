package ru.nstu.system.security;

import java.util.Set;

/**
 * Canonical role names shared by every service (design.md D10).
 *
 * <p>Names carry no {@code ROLE_} prefix; authorities are built from them by
 * {@link RoleHierarchyFactory#toAuthorities(java.util.Collection)}.</p>
 */
public final class RoleNames {

    public static final String ADMIN = "ADMIN";

    public static final String STAFF = "STAFF";

    public static final String STUDENT = "STUDENT";

    public static final String GUEST = "GUEST";

    /** Prefix required by Spring Security for role-based authorities. */
    public static final String ROLE_PREFIX = "ROLE_";

    /** All roles known to the system, ordered from most to least privileged. */
    public static final Set<String> ALL = Set.of(ADMIN, STAFF, STUDENT, GUEST);

    private RoleNames() {
    }
}
