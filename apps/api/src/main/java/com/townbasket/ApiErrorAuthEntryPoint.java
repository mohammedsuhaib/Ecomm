package com.townbasket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Emits a 401 in the shared {@link ApiError} JSON shape when an unauthenticated
 * request hits a protected route (so the response matches every other error the
 * storefront/admin clients parse). Used by the security filter chain.
 */
class ApiErrorAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    ApiErrorAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication is required to access this resource",
                request.getRequestURI());
        write(response, HttpStatus.UNAUTHORIZED, body, objectMapper);
    }

    static void write(HttpServletResponse response, HttpStatus status, ApiError body, ObjectMapper mapper)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), body);
    }
}
