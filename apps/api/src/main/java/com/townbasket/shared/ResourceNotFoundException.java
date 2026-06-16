package com.townbasket.shared;

/**
 * Signals that a requested resource does not exist. Lives in the OPEN
 * {@code shared} module so any module can throw it and the global handler maps
 * it to HTTP 404 with the shared {@link ApiError} shape.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
