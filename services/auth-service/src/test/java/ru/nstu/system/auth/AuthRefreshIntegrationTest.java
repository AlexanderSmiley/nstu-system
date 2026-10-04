package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.auth.domain.RefreshToken;

/** Task 5.2: refresh-token rotation, reuse detection and expiry handling. */
class AuthRefreshIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void validRefreshRotatesTokenAndInvalidatesTheOldOne() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String firstRefresh = cookieValue(login, REFRESH_COOKIE);

        MvcResult rotated = refresh(firstRefresh);

        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        String secondRefresh = cookieValue(rotated, REFRESH_COOKIE);
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        assertThat(setCookieHeader(rotated, ACCESS_COOKIE)).isNotNull();

        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHash(refreshTokenGenerator.hash(firstRefresh))
                .orElseThrow();
        RefreshToken newToken = refreshTokenRepository
                .findByTokenHash(refreshTokenGenerator.hash(secondRefresh))
                .orElseThrow();
        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(newToken.isRevoked()).isFalse();
        assertThat(newToken.getRotatedFrom()).isEqualTo(oldToken.getId());

        // The freshly rotated token still works.
        assertThat(refresh(secondRefresh).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void reusingRevokedRefreshTokenReturns401AndRevokesAllAccountTokens() throws Exception {
        MvcResult login = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String firstRefresh = cookieValue(login, REFRESH_COOKIE);
        String secondRefresh = cookieValue(refresh(firstRefresh), REFRESH_COOKIE);

        MvcResult reuse = refresh(firstRefresh);

        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(reuse.getResponse().getContentAsString()).contains("invalid_refresh_token");
        assertThat(refreshTokenRepository.countByAccountIdAndRevokedFalse(adminId())).isZero();
        assertThat(refresh(secondRefresh).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unknownRefreshTokenReturns401() throws Exception {
        MvcResult result = refresh("this-token-was-never-issued");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_refresh_token");
    }

    @Test
    void expiredRefreshTokenReturns401() throws Exception {
        String raw = "expired-refresh-token-value";
        refreshTokenRepository.save(RefreshToken.issue(
                adminId(),
                refreshTokenGenerator.hash(raw),
                Instant.now().minus(Duration.ofHours(1)),
                null));

        MvcResult result = refresh(raw);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
