package com.townbasket.identity;

/**
 * Admin view of a password-login account (STORE_STAFF or ADMIN). No password
 * material is ever exposed.
 */
public record StaffMemberDto(Long id, String name, String email, String role, boolean active) {
}
