package ru.nstu.system.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing configuration (design.md D9).
 *
 * <p>Provides the BCrypt {@link PasswordEncoder} used by the administrator
 * bootstrap and the authentication flows. The servlet filter chain itself is
 * configured in {@link AuthSecurityConfig}.</p>
 */
@Configuration
public class CryptoConfig {

    /** BCrypt work factor; the Spring Security default (10). */
    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
