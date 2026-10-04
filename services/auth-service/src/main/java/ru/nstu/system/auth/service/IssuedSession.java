package ru.nstu.system.auth.service;

import java.time.Duration;
import ru.nstu.system.auth.web.dto.ProfileResponse;

/**
 * Result of a successful account session operation (login, refresh, password
 * change): the tokens to place in cookies plus the profile to return.
 *
 * @param profile      account profile for the response body
 * @param accessToken  signed JWT to set in the {@code access_token} cookie
 * @param refreshToken opaque refresh token to set in the {@code refresh_token} cookie
 * @param accessTtl    access-token lifetime, i.e. the access cookie {@code Max-Age}
 * @param refreshTtl   refresh-token lifetime, i.e. the refresh cookie {@code Max-Age}
 */
public record IssuedSession(
        ProfileResponse profile,
        String accessToken,
        String refreshToken,
        Duration accessTtl,
        Duration refreshTtl) {
}
