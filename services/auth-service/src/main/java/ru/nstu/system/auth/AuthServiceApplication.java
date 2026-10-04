package ru.nstu.system.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import ru.nstu.system.security.PackageMarker;

/**
 * Authentication service entry point.
 *
 * <p>The component scan is explicit: only this service and
 * {@code ru.nstu.system.security} (JWT parser/issuer and shared policies) are
 * scanned. Scanning the whole {@code ru.nstu.system} tree would activate
 * {@code NstuRabbitConfig} from {@code ru.nstu:contracts}, which this service does
 * not use.</p>
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded because
 * authentication is performed manually against {@code auth.account}; without the
 * exclusion Spring Boot would create a default in-memory user and log a generated
 * password (design.md D6, D9).</p>
 */
@SpringBootApplication(
        scanBasePackages = {
                "ru.nstu.system.auth",
                "ru.nstu.system.security"
        },
        exclude = UserDetailsServiceAutoConfiguration.class)
public class AuthServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceApplication.class);

    /**
     * Compile-time proof that the {@code ru.nstu:security} composite-build module
     * is on the compile classpath without being published to any repository.
     */
    private static final String SECURITY_MODULE = PackageMarker.moduleName();

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
        log.debug("Shared security module wired from composite build: {}", SECURITY_MODULE);
    }
}
