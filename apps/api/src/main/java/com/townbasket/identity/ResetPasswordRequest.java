package com.townbasket.identity;

/**
 * Admin-set password for another account ({@code POST /admin/users/{id}/password}).
 * Unlike {@link ChangePasswordRequest} there is no current password — the point
 * is that the account holder has forgotten it. Min 8 characters.
 */
public record ResetPasswordRequest(String newPassword) {
}
