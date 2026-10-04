package ru.nstu.system.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.nstu.system.security.JwtProperties;
import ru.nstu.system.security.TokenIssuer;

/**
 * Token issuers used by {@code auth-service}.
 *
 * <p>Account sessions use the component-scanned {@code tokenIssuer} with the shared
 * access-token lifetime (15 minutes). Guest sessions need a much longer lifetime
 * (7 days, design.md D11) and no refresh token, so a second issuer is built from the
 * same secret and issuer but with {@code nstu.auth.guest-ttl}. Both produce tokens
 * accepted by the shared {@link ru.nstu.system.security.AccessTokenParser}.</p>
 *
 * <p>Callers must disambiguate the two {@link TokenIssuer} beans by qualifier
 * ({@code tokenIssuer} / {@code guestTokenIssuer}).</p>
 */
@Configuration
public class TokenConfig {

    /**
     * @param jwtProperties  shared secret/issuer configuration
     * @param authProperties guest lifetime
     * @return an issuer that signs guest tokens with the configured guest TTL
     */
    @Bean
    public TokenIssuer guestTokenIssuer(JwtProperties jwtProperties, AuthProperties authProperties) {
        JwtProperties guest = new JwtProperties();
        guest.setIssuer(jwtProperties.getIssuer());
        guest.setSecret(jwtProperties.getSecret());
        guest.setAccessTtl(authProperties.getGuestTtl());
        return new TokenIssuer(guest, Clock.systemUTC());
    }
}
