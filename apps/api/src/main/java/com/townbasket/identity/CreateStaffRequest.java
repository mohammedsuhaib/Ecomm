package com.townbasket.identity;

/**
 * Admin request to create a password-login staff account
 * ({@code POST /admin/staff}).
 *
 * <p>{@code role} is the {@link Role} name and must be {@code STORE_STAFF} or
 * {@code ADMIN} — matching case is not required. Riders are deliberately NOT
 * creatable here: they have their own on-duty lifecycle and are onboarded
 * through {@code POST /admin/delivery-agents}. Customers have no password at
 * all; they sign in by phone OTP.
 *
 * <p>{@code password} must be at least 8 characters and {@code email} must be
 * unique across every account.
 */
public record CreateStaffRequest(String name, String email, String password, String role) {
}
