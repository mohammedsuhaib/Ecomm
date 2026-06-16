package com.townbasket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Emits a 403 in the shared {@link ApiError} JSON shape when an authenticated
 * caller lacks the required role (e.g. a CUSTOMER hitting {@code /admin/**}).
 */
class ApiErrorAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    ApiErrorAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "You do not have permission to access this resource",
                request.getRequestURI());
        ApiErrorAuthEntryPoint.write(response, HttpStatus.FORBIDDEN, body, objectMapper);
    }
}
