package com.townbasket.identity;

/**
 * The credentials were RIGHT, but the account has been switched off by an
 * administrator. Mapped to HTTP 403 by the global handler, distinct from
 * {@link InvalidCredentialsException}'s 401, so the login screen can say
 * "your account is deactivated" instead of "wrong password" — the person
 * knows their password perfectly well, and being told otherwise sends them to
 * a reset flow that cannot help.
 *
 * <p>Thrown only after the password has been verified. A wrong password on a
 * deactivated account is still a plain 401, so nobody can learn which accounts
 * exist, or which are switched off, without already holding the credential.
 */
public class AccountDeactivatedException extends RuntimeException {

    public AccountDeactivatedException(String message) {
        super(message);
    }
}
