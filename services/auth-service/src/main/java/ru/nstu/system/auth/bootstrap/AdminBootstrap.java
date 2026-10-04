package ru.nstu.system.auth.bootstrap;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.nstu.system.auth.domain.Account;
import ru.nstu.system.auth.domain.AccountRepository;
import ru.nstu.system.auth.domain.Role;

/**
 * Creates the single administrator account on first startup
 * (design.md D2/D9, identity spec "Бутстрап администратора при установке").
 *
 * <p>Credentials are read from {@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD}.
 * Spring's relaxed system-environment binding resolves both the uppercase
 * environment variables (production) and the {@code admin.username}/
 * {@code admin.password} properties (tests).</p>
 *
 * <ul>
 *   <li>No {@code ADMIN} account and both variables set → create exactly one
 *       administrator with {@code mustChangePassword = true}.</li>
 *   <li>An {@code ADMIN} already exists → do nothing (no duplicate, no password
 *       overwrite).</li>
 *   <li>Variables missing/blank → start normally, log a warning, create nothing.</li>
 * </ul>
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /** Partial unique index enforcing the single-ADMIN invariant. */
    private static final String SINGLE_ADMIN_CONSTRAINT = "account_single_admin_uniq";

    /** Unique constraints on the account username (raw and normalized). */
    private static final String USERNAME_CONSTRAINT = "account_username_uniq";
    private static final String USERNAME_NORMALIZED_CONSTRAINT = "account_username_normalized_uniq";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrap(AccountRepository accountRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${admin.username:}") String adminUsername,
                          @Value("${admin.password:}") String adminPassword) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        bootstrap();
    }

    /**
     * Runs the idempotent administrator bootstrap.
     *
     * @return {@code true} if an administrator account was created, {@code false}
     *         otherwise (already present, credentials absent, username taken or a
     *         concurrent instance created the administrator first)
     */
    public boolean bootstrap() {
        boolean usernameMissing = !StringUtils.hasText(adminUsername);
        boolean passwordMissing = !StringUtils.hasText(adminPassword);
        if (usernameMissing || passwordMissing) {
            // Never log the values themselves; only which parameter is absent.
            log.warn("Administrator bootstrap skipped: {} not set; starting without an administrator, "
                            + "login is impossible until an administrator account exists",
                    missingParameters(usernameMissing, passwordMissing));
            return false;
        }

        if (accountRepository.existsByRole(Role.ADMIN)) {
            log.info("An administrator account already exists; administrator bootstrap is skipped");
            return false;
        }

        String normalized = normalize(adminUsername);
        if (accountRepository.findByUsernameNormalized(normalized).isPresent()) {
            log.warn("Administrator bootstrap skipped: username '{}' is already taken by a non-admin account",
                    adminUsername);
            return false;
        }

        Account admin = Account.createAdmin(adminUsername, normalized, passwordEncoder.encode(adminPassword));
        try {
            accountRepository.saveAndFlush(admin);
            log.info("Bootstrapped administrator account '{}' with mandatory password change", adminUsername);
            return true;
        } catch (DataIntegrityViolationException ex) {
            String causeMessage = ex.getMostSpecificCause().getMessage();
            if (causeMessage != null && causeMessage.contains(SINGLE_ADMIN_CONSTRAINT)) {
                // Another instance created its ADMIN between our existsByRole check
                // and this insert. The partial unique index keeps the invariant; we
                // simply yield and keep the instance that got there first.
                log.warn("Administrator bootstrap skipped: a concurrent instance already created the "
                        + "administrator (unique constraint '{}')", SINGLE_ADMIN_CONSTRAINT);
                return false;
            }
            if (causeMessage != null && (causeMessage.contains(USERNAME_CONSTRAINT)
                    || causeMessage.contains(USERNAME_NORMALIZED_CONSTRAINT))) {
                log.warn("Administrator bootstrap skipped: username '{}' was taken concurrently by a "
                        + "non-admin account (unique constraint on account username)", adminUsername);
                return false;
            }
            // Unrelated integrity violation: this is a configuration/programming
            // error, not a bootstrap race. Surface it instead of masking it.
            log.error("Administrator bootstrap failed with an unexpected data integrity violation: {}",
                    causeMessage, ex);
            throw ex;
        }
    }

    private static String missingParameters(boolean usernameMissing, boolean passwordMissing) {
        if (usernameMissing && passwordMissing) {
            return "ADMIN_USERNAME and ADMIN_PASSWORD are";
        }
        return (usernameMissing ? "ADMIN_USERNAME is" : "ADMIN_PASSWORD is");
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
