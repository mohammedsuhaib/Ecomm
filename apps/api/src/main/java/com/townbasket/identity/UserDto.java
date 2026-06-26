package com.townbasket.identity;

/**
 * Public user representation. {@code role} is the {@link Role} name. Any of
 * {@code name} / {@code phone} / {@code email} may be null (a fresh phone-OTP
 * customer has no name/email; staff have no phone).
 */
public record UserDto(
        Long id,
        String role,
        String name,
        String phone,
        String email) {
}
