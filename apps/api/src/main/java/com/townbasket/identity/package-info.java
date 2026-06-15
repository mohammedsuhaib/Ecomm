/**
 * {@code identity} module — users, addresses and refresh tokens.
 *
 * <p>Phone-OTP login is delegated to Firebase Auth; the backend verifies the
 * Firebase ID token once and issues its own JWTs (access + refresh). Staff and
 * admins log in with email + password. Roles: {@code CUSTOMER},
 * {@code STORE_STAFF}, {@code ADMIN}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.townbasket.identity;
