package com.townbasket.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints under {@code /api/v1/auth} — all PUBLIC (the security
 * layer permits {@code /auth/**}). Customer phone login, staff email/password
 * login, refresh-token rotation and logout. Returns the identity module's
 * published DTOs only.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Phone-OTP / staff login, token refresh and logout.")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/phone/verify")
    @Operation(summary = "Customer login/signup from a Firebase ID token (dev: dev:<phone>).")
    AuthResponse phoneVerify(@RequestBody PhoneVerifyRequest request) {
        return authService.phoneVerify(request);
    }

    @PostMapping("/staff/login")
    @Operation(summary = "Staff/admin login by email + password.")
    AuthResponse staffLogin(@RequestBody StaffLoginRequest request) {
        return authService.staffLogin(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token; returns a new access+refresh pair.")
    TokenPair refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token (idempotent).")
    void logout(@RequestBody LogoutRequest request) {
        authService.logout(request);
    }
}
