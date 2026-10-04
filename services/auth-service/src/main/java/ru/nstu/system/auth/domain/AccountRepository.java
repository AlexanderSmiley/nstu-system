package ru.nstu.system.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Account}. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByRole(Role role);

    long countByRole(Role role);

    Optional<Account> findByUsernameNormalized(String usernameNormalized);
}
