package ru.nstu.system.auth.service;

import java.time.Duration;
import ru.nstu.system.auth.web.dto.MeResponse;

/**
 * Result of creating an anonymous guest session (design.md D11): a single long-lived
 * access token, without a refresh token and without any persisted account.
 *
 * @param profile     guest representation for the response body
 * @param accessToken signed JWT with {@code sub = guest:<uuid>} and
 *                    {@code roles = [GUEST]}
 * @param accessTtl   guest lifetime, i.e. the access cookie {@code Max-Age}
 */
public record IssuedGuestSession(
        MeResponse profile,
        String accessToken,
        Duration accessTtl) {
}
