package ru.nstu.system.student.web;

import java.util.UUID;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.RoleNames;
import ru.nstu.system.security.SecurityContextSupport;
import ru.nstu.system.student.error.ApiException;

/**
 * Resolves the authenticated account id of the current request for the servlet
 * controllers.
 *
 * <p>A missing token cannot reach a controller (the security filter chain
 * answers 401), but the check is kept as a defensive fallback. A guest session is
 * authenticated yet owns no account, so it is rejected with 403 and a
 * capability-specific code. Any non-UUID subject (for example a guest) is treated
 * as unauthenticated.</p>
 */
final class AccountIdentity {

    private AccountIdentity() {
    }

    /**
     * @param guestCode    stable error code for a guest session
     * @param guestMessage human-readable guest error message
     * @return the caller's account id
     */
    static UUID requireAccountId(String guestCode, String guestMessage) {
        ParsedToken token = SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
        if (token.roles().contains(RoleNames.GUEST)) {
            throw ApiException.forbidden(guestCode, guestMessage);
        }
        try {
            return UUID.fromString(token.subject());
        } catch (IllegalArgumentException exception) {
            throw ApiException.unauthorized("unauthorized", "Требуется аутентификация");
        }
    }
}
