package com.townbasket.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile + saved-address endpoints under {@code /api/v1/me} — AUTHENTICATED
 * (the security layer requires a valid token). The caller's user id is the
 * security principal (a plain {@code Long} set by the JWT filter), so every
 * operation is implicitly owner-scoped.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me", description = "Current-user profile and saved addresses.")
class MeController {

    private final AuthService authService;

    MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @Operation(summary = "Current user's profile.")
    UserDto me(@AuthenticationPrincipal Long userId) {
        return authService.currentUser(userId);
    }

    @PutMapping
    @Operation(summary = "Update the current user's display name (1..80 chars; 400 otherwise).")
    UserDto updateProfile(@RequestBody UpdateProfileRequest request, @AuthenticationPrincipal Long userId) {
        return authService.updateProfile(userId, request == null ? null : request.name());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the current user's password (staff/admin only).")
    void changePassword(@RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Long userId) {
        authService.changePassword(
                userId,
                request == null ? null : request.currentPassword(),
                request == null ? null : request.newPassword());
    }

    /** Rider availability — DELIVERY_AGENT only (403 otherwise). */
    @GetMapping("/duty")
    @Operation(summary = "The calling rider's on-duty status.")
    DutyStatusDto duty(@AuthenticationPrincipal Long userId) {
        return authService.dutyStatus(userId);
    }

    /**
     * Rider goes on/off duty. Off duty means no NEW assignments; orders already
     * held stay in their queue until delivered or reported.
     */
    @PutMapping("/duty")
    @Operation(summary = "Set the calling rider's on-duty status.")
    DutyStatusDto setDuty(@RequestBody DutyStatusDto request, @AuthenticationPrincipal Long userId) {
        return authService.setDutyStatus(userId, request != null && request.onDuty());
    }

    @GetMapping("/addresses")
    @Operation(summary = "List the user's saved addresses (default first, then newest).")
    List<SavedAddressDto> listAddresses(@AuthenticationPrincipal Long userId) {
        return authService.listAddresses(userId);
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a saved address.")
    SavedAddressDto addAddress(@RequestBody AddressInput input, @AuthenticationPrincipal Long userId) {
        return authService.addAddress(userId, input);
    }

    @PutMapping("/addresses/{id}")
    @Operation(summary = "Update one of the user's addresses (404 if not owned).")
    SavedAddressDto updateAddress(@PathVariable Long id, @RequestBody AddressInput input,
                                  @AuthenticationPrincipal Long userId) {
        return authService.updateAddress(userId, id, input);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of the user's addresses (404 if not owned).")
    void deleteAddress(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        authService.deleteAddress(userId, id);
    }
}
