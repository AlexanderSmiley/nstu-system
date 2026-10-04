package ru.nstu.system.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Single definition of the role hierarchy {@code ADMIN > STAFF > STUDENT > GUEST}
 * (design.md D10, spec "Иерархия ролей").
 *
 * <p>The hierarchy is expressed in terms of {@code ROLE_}-prefixed authorities,
 * which is what Spring Security checks. An {@code ADMIN} authority therefore
 * satisfies any {@code hasRole('STAFF'|'STUDENT'|'GUEST')} check.</p>
 */
public final class RoleHierarchyFactory {

    private static final RoleHierarchy ROLE_HIERARCHY = RoleHierarchyImpl.fromHierarchy("""
            ROLE_ADMIN > ROLE_STAFF
            ROLE_STAFF > ROLE_STUDENT
            ROLE_STUDENT > ROLE_GUEST
            """);

    private RoleHierarchyFactory() {
    }

    /**
     * @return the shared, immutable role hierarchy
     */
    public static RoleHierarchy roleHierarchy() {
        return ROLE_HIERARCHY;
    }

    /**
     * @return a converter turning bare role names into {@code ROLE_}-prefixed
     *         authorities, usable in custom authentication converters
     */
    public static Converter<Collection<String>, Collection<GrantedAuthority>> roleAuthorityConverter() {
        return RoleHierarchyFactory::toAuthorities;
    }

    /**
     * Maps bare role names to {@code ROLE_}-prefixed authorities.
     *
     * @param roles role names without the prefix; {@code null} yields an empty list
     */
    public static Collection<GrantedAuthority> toAuthorities(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Collection<GrantedAuthority> authorities = new ArrayList<>(roles.size());
        Set<String> seen = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String name = role.startsWith(RoleNames.ROLE_PREFIX)
                    ? role
                    : RoleNames.ROLE_PREFIX + role;
            if (seen.add(name)) {
                authorities.add(new SimpleGrantedAuthority(name));
            }
        }
        return authorities;
    }
}
