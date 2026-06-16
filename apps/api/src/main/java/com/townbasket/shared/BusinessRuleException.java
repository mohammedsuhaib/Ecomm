package com.townbasket.shared;

/**
 * Signals that a request is well-formed but violates a business rule (e.g. order
 * below the minimum value, address out of the delivery radius, insufficient
 * stock, or an illegal order-status transition). Lives in the OPEN
 * {@code shared} module so any module can throw it and the global handler maps
 * it to HTTP 422 with the shared {@link ApiError} shape.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
