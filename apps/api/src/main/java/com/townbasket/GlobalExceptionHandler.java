package com.townbasket;

import com.townbasket.identity.InvalidCredentialsException;
import com.townbasket.inventory.InsufficientStockException;
import com.townbasket.shared.ApiError;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Failed authentication (bad phone token, wrong staff credentials, invalid
     * refresh token) -> 401. Distinct from the security filter's 401 for a
     * missing/invalid Bearer token on a protected route.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleUnauthorized(InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /** Missing/unknown resources (e.g. cart or order id) -> 404. */
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Insufficient stock is a transient <em>conflict</em> (the requested quantity
     * is no longer available right now) -> 409. Mapped ahead of the more general
     * {@link BusinessRuleException} so the storefront can show a precise
     * "out of stock / reduce quantity" message rather than the generic 422 copy.
     */
    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ApiError> handleConflictStock(InsufficientStockException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Business-rule violations on a well-formed request (min order value, out of
     * delivery radius, item no longer available, store closed, price changed,
     * illegal order transition) -> 422.
     */
    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> handleUnprocessable(BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
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
