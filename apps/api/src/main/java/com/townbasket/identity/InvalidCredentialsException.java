package com.townbasket.identity;

/**
 * Signals failed authentication: a bad/expired phone token, wrong staff
 * email/password, or an invalid refresh token. The global handler maps it to
 * HTTP 401 with the shared {@code ApiError} shape. Carries no detail about
 * which factor failed (avoids credential enumeration).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
