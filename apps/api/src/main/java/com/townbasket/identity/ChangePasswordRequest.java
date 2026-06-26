package com.townbasket.identity;

/**
 * Password-change payload for {@code POST /api/v1/me/password}. Only accounts
 * that HAVE a password (STORE_STAFF / ADMIN) may use this; the current password
 * is BCrypt-verified and the new password must be at least 8 characters and
 * different from the current one.
 */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
