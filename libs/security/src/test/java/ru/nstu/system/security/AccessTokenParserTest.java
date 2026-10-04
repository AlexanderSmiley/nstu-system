package ru.nstu.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for token issuance and parsing: valid, expired, forged, wrong issuer
 * and the restricted password-change flag.
 */
class AccessTokenParserTest {

    private static final String SECRET =
            "unit-test-secret-value-0123456789-abcdefghij";
    private static final String OTHER_SECRET =
            "another-unit-test-secret-9876543210-zyxwvutsr";

    private static JwtProperties properties(String issuer, String secret, Duration ttl) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer(issuer);
        properties.setSecret(secret);
        properties.setAccessTtl(ttl);
        return properties;
    }

    @Test
    void parsesValidTokenAndPreservesClaims() {
        JwtProperties properties = properties("nstu-system", SECRET, Duration.ofMinutes(15));
        TokenIssuer issuer = new TokenIssuer(properties);
        AccessTokenParser parser = new AccessTokenParser(properties);

        Instant before = Instant.now();
        String token = issuer.issueAccessToken(
                "acc-1", Set.of(RoleNames.STUDENT, RoleNames.GUEST), false);
        ParsedToken parsed = parser.parse(token);

        assertThat(parsed.subject()).isEqualTo("acc-1");
        assertThat(parsed.roles()).containsExactlyInAnyOrder(RoleNames.STUDENT, RoleNames.GUEST);
        assertThat(parsed.passwordChangeRequired()).isFalse();
        assertThat(parsed.issuedAt()).isAfterOrEqualTo(before.minusSeconds(2));
        assertThat(parsed.expiresAt()).isAfter(parsed.issuedAt());
        assertThat(Duration.between(parsed.issuedAt(), parsed.expiresAt()))
                .isCloseTo(Duration.ofMinutes(15), Duration.ofSeconds(1));
    }

    @Test
    void parsesPasswordChangeRequiredFlag() {
        JwtProperties properties = properties("nstu-system", SECRET, Duration.ofMinutes(15));
        TokenIssuer issuer = new TokenIssuer(properties);
        AccessTokenParser parser = new AccessTokenParser(properties);

        String token = issuer.issueAccessToken("guest:abc", Set.of(RoleNames.GUEST), true);
        ParsedToken parsed = parser.parse(token);

        assertThat(parsed.subject()).isEqualTo("guest:abc");
        assertThat(parsed.roles()).containsExactly(RoleNames.GUEST);
        assertThat(parsed.passwordChangeRequired()).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        JwtProperties properties = properties("nstu-system", SECRET, Duration.ofHours(-1));
        String token = new TokenIssuer(properties)
                .issueAccessToken("acc-1", Set.of(RoleNames.STAFF), false);
        AccessTokenParser parser = new AccessTokenParser(properties);

        assertThatThrownBy(() -> parser.parse(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtProperties issuing = properties("nstu-system", SECRET, Duration.ofMinutes(15));
        String token = new TokenIssuer(issuing)
                .issueAccessToken("acc-1", Set.of(RoleNames.STAFF), false);
        AccessTokenParser parser = new AccessTokenParser(
                properties("nstu-system", OTHER_SECRET, Duration.ofMinutes(15)));

        assertThatThrownBy(() -> parser.parse(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtProperties issuing = properties("nstu-system", SECRET, Duration.ofMinutes(15));
        String token = new TokenIssuer(issuing)
                .issueAccessToken("acc-1", Set.of(RoleNames.STAFF), false);
        AccessTokenParser parser = new AccessTokenParser(
                properties("evil-system", SECRET, Duration.ofMinutes(15)));

        assertThatThrownBy(() -> parser.parse(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsMalformedAndMissingTokens() {
        AccessTokenParser parser = new AccessTokenParser(
                properties("nstu-system", SECRET, Duration.ofMinutes(15)));

        assertThatThrownBy(() -> parser.parse("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsWeakSecretOnConstruction() {
        JwtProperties weak = properties("nstu-system", "too-short", Duration.ofMinutes(15));

        assertThatThrownBy(() -> new AccessTokenParser(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least");
    }
}
