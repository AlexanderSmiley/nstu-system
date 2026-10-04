package ru.nstu.system.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.Role;
import ru.nstu.system.auth.web.ApiException;

/**
 * Unit tests for the administrator-safety policy (task 5.9).
 *
 * <p>The block-related rules are covered here because this task only implements
 * role editing; the corresponding HTTP endpoints arrive with task 5.10.</p>
 */
class UserAdminPolicyTest {

    private final UserAdminPolicy policy = new UserAdminPolicy();

    // --- role assignment -------------------------------------------------------

    @Test
    void assignableRolesAreStaffAndStudent() {
        assertThat(policy.requireAssignableRole("STAFF")).isEqualTo(Role.STAFF);
        assertThat(policy.requireAssignableRole("student")).isEqualTo(Role.STUDENT);
        assertThat(policy.requireAssignableRole("  Staff ")).isEqualTo(Role.STAFF);
    }

    @Test
    void adminRoleCannotBeAssigned() {
        assertApiException(policy::requireAssignableRole, "ADMIN", "admin_not_assignable");
    }

    @Test
    void guestRoleCannotBeAssigned() {
        assertApiException(policy::requireAssignableRole, "GUEST", "guest_not_assignable");
    }

    @Test
    void unknownRoleIsRejected() {
        assertApiException(policy::requireAssignableRole, "SUPERUSER", "invalid_role");
        assertApiException(policy::requireAssignableRole, "  ", "invalid_role");
        assertApiException(policy::requireAssignableRole, null, "invalid_role");
    }

    // --- role changes ----------------------------------------------------------

    @Test
    void changingAnotherAccountsRoleIsAllowed() {
        Account admin = account("root", Role.ADMIN);
        Account staff = account("staff", Role.STAFF);

        assertThatCode(() -> policy.ensureRoleChangeAllowed(admin.getId(), staff, Role.STUDENT))
                .doesNotThrowAnyException();
    }

    @Test
    void keepingOwnRoleIsAllowed() {
        Account admin = account("root", Role.ADMIN);

        assertThatCode(() -> policy.ensureRoleChangeAllowed(admin.getId(), admin, Role.ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    void selfDemotionIsRejected() {
        Account admin = account("root", Role.ADMIN);

        assertThatThrownBy(() -> policy.ensureRoleChangeAllowed(admin.getId(), admin, Role.STUDENT))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertCode(ex, "self_demotion_forbidden"));
    }

    @Test
    void demotingTheLastAdministratorIsRejected() {
        Account admin = account("root", Role.ADMIN);
        Account staff = account("staff", Role.STAFF);

        assertThatThrownBy(() -> policy.ensureRoleChangeAllowed(staff.getId(), admin, Role.STUDENT))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertCode(ex, "last_admin_required"));
    }

    // --- blocking --------------------------------------------------------------

    @Test
    void selfBlockIsRejected() {
        Account admin = account("root", Role.ADMIN);

        assertThatThrownBy(() -> policy.ensureBlockAllowed(admin.getId(), admin))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertCode(ex, "self_block_forbidden"));
    }

    @Test
    void blockingTheLastAdministratorIsRejected() {
        Account admin = account("root", Role.ADMIN);
        Account staff = account("staff", Role.STAFF);

        assertThatThrownBy(() -> policy.ensureBlockAllowed(staff.getId(), admin))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertCode(ex, "last_admin_required"));
    }

    @Test
    void blockingAnotherAccountIsAllowed() {
        Account admin = account("root", Role.ADMIN);
        Account staff = account("staff", Role.STAFF);

        assertThatCode(() -> policy.ensureBlockAllowed(admin.getId(), staff))
                .doesNotThrowAnyException();
    }

    private static Account account(String username, Role role) {
        return Account.create(username, username, "$2a$10$hash", username, null, role, false, false);
    }

    private static void assertApiException(java.util.function.Consumer<String> call,
                                           String role,
                                           String expectedCode) {
        assertThatThrownBy(() -> call.accept(role))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertCode(ex, expectedCode));
    }

    private static void assertCode(Throwable throwable, String expectedCode) {
        ApiException exception = (ApiException) throwable;
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getCode()).isEqualTo(expectedCode);
        assertThat(exception.getMessage()).isNotBlank();
    }
}
