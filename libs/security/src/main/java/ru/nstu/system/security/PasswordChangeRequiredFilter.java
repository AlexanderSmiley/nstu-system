package ru.nstu.system.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter enforcing the restricted-token policy (design.md D7).
 *
 * <p>When a request carries a {@link ParsedToken} with
 * {@link ParsedToken#passwordChangeRequired()} set, only the routes allowlisted
 * by {@link PasswordChangePolicy} pass; every other request is rejected with
 * {@code 403 Forbidden}. Requests without a parsed token are ignored: they are
 * either anonymous (handled elsewhere) or already rejected upstream.</p>
 *
 * <p>The filter must run after the service's authentication filter has called
 * {@link SecurityContextSupport#attach(HttpServletRequest, ParsedToken)}.</p>
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final int rejectionStatus;

    public PasswordChangeRequiredFilter() {
        this(HttpServletResponse.SC_FORBIDDEN);
    }

    public PasswordChangeRequiredFilter(int rejectionStatus) {
        this.rejectionStatus = rejectionStatus;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ParsedToken token = SecurityContextSupport.tokenFromRequest(request).orElse(null);
        if (token != null && token.passwordChangeRequired()
                && !PasswordChangePolicy.isAllowed(request.getMethod(), pathWithinApplication(request))) {
            response.sendError(rejectionStatus, "Password change required");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()
                && requestUri != null && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }
}
