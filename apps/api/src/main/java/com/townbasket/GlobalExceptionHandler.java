package com.townbasket;

import com.townbasket.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Application-wide error handler producing the shared {@link ApiError} shape.
 * Kept deliberately small — only the handful of cases the storefront can hit.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> handleConflict(IllegalStateException ex, HttpServletRequest request) {
        // e.g. no active store configured for a serviceability check.
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
