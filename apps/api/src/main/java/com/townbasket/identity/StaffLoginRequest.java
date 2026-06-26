package com.townbasket.identity;

/** Staff/admin login request: email + password (BCrypt-verified). */
public record StaffLoginRequest(String email, String password) {
}
