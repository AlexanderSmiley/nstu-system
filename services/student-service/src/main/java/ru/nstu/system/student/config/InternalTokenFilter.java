package ru.nstu.system.student.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the {@code /internal/**} namespace with a shared secret header
 * (design.md D12, task 6.3).
 *
 * <p>The internal API is not routed by the gateway and is not reachable from
 * outside the private network; this header is a defence-in-depth check for the
 * remaining in-network callers (notably {@code event-service}). Missing, blank or
 * wrong tokens yield {@code 403 Forbidden}. The comparison uses
 * {@link MessageDigest#isEqual(byte[], byte[])} so it does not leak the expected
 * token through timing.</p>
 *
 * <p>Only requests matching {@code /internal/**} reach this filter: it is added
 * exclusively to the dedicated security filter chain of that chain.</p>
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    /** Header carrying the shared internal secret. */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final String FORBIDDEN_BODY = "{\"error\":\"forbidden\"}";

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final byte[] expectedToken;

    public InternalTokenFilter(NstuInternalProperties properties) {
        String token = properties == null ? null : properties.getToken();
        this.expectedToken = StringUtils.hasText(token) ? token.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String provided = request.getHeader(INTERNAL_TOKEN_HEADER);
        byte[] providedToken = StringUtils.hasText(provided)
                ? provided.getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        // Fail closed: an unconfigured token rejects everything.
        if (expectedToken.length == 0 || providedToken.length == 0
                || !MessageDigest.isEqual(expectedToken, providedToken)) {
            writeForbidden(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(FORBIDDEN_BODY);
    }
}
