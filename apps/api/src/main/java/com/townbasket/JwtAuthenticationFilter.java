package com.townbasket;

import com.townbasket.identity.AuthenticatedUser;
import com.townbasket.identity.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless authentication filter for our own access tokens. Reads the token
 * from the {@code Authorization: Bearer} header, or — for GET requests only —
 * the {@code token} query parameter (so {@code EventSource}, which cannot send
 * headers, can authenticate the admin SSE stream). On a valid token it sets a
 * {@link UsernamePasswordAuthenticationToken} whose principal is the
 * {@code Long} user id and whose single authority is {@code ROLE_<role>}.
 *
 * <p>The filter NEVER rejects a request: a missing/invalid token simply leaves
 * the request unauthenticated, and the authorization rules (or the entry point)
 * decide the outcome. This keeps PUBLIC endpoints reachable and lets
 * {@code POST /orders} tie to a user only when a token is present.
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            tokenService.authenticate(token).ifPresent(user -> authenticate(user, request));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(AuthenticatedUser user, HttpServletRequest request) {
        var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
        var authentication = new UsernamePasswordAuthenticationToken(
                user.userId(), null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String value = header.substring(BEARER_PREFIX.length()).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        // GET only: allow ?token= for EventSource (admin SSE) which can't set headers.
        // OPS NOTE: a token in the query string can leak into reverse-proxy / access
        // logs and browser history. Only the short-lived ACCESS token is ever sent
        // this way (never the refresh token), and this app does not log it; configure
        // the production proxy (Caddy) to omit query strings for /admin/orders/stream.
        if (HttpMethod.GET.matches(request.getMethod())) {
            String param = request.getParameter("token");
            if (param != null && !param.isBlank()) {
                return param.trim();
            }
        }
        return null;
    }
}
