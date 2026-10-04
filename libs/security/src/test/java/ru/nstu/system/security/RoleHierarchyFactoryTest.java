package ru.nstu.system.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Tests for the {@code ADMIN > STAFF > STUDENT > GUEST} hierarchy and the role
 * to authority conversion.
 */
class RoleHierarchyFactoryTest {

    private final RoleHierarchy hierarchy = RoleHierarchyFactory.roleHierarchy();

    private Set<String> reachable(String role) {
        Collection<? extends GrantedAuthority> authorities =
                hierarchy.getReachableGrantedAuthorities(
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void adminReachesEveryRole() {
        assertThat(reachable(RoleNames.ADMIN))
                .containsExactlyInAnyOrder(
                        "ROLE_ADMIN", "ROLE_STAFF", "ROLE_STUDENT", "ROLE_GUEST");
    }

    @Test
    void staffReachesStudentAndGuestButNotAdmin() {
        assertThat(reachable(RoleNames.STAFF))
                .containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_STUDENT", "ROLE_GUEST")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void studentReachesGuestButNotStaff() {
        assertThat(reachable(RoleNames.STUDENT))
                .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_GUEST")
                .doesNotContain("ROLE_STAFF", "ROLE_ADMIN");
    }

    @Test
    void guestReachesOnlyGuest() {
        assertThat(reachable(RoleNames.GUEST))
                .containsExactly("ROLE_GUEST");
    }

    @Test
    void converterPrefixesBareRoleNames() {
        Collection<GrantedAuthority> authorities =
                RoleHierarchyFactory.roleAuthorityConverter().convert(List.of("ADMIN", "GUEST"));

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_GUEST");
    }

    @Test
    void converterDoesNotDoublePrefixAndSkipsBlanks() {
        Collection<GrantedAuthority> authorities =
                RoleHierarchyFactory.toAuthorities(java.util.Arrays.asList("ROLE_STAFF", " ", null));

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STAFF");
    }
}
