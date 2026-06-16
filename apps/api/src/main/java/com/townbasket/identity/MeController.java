package com.townbasket.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * (the security layer requires a valid token). The caller's user id is read
 * from the {@code SecurityContext} principal (a plain {@code Long} set by the
 * JWT filter), so every operation is implicitly owner-scoped.
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
    UserDto me() {
        return authService.currentUser(currentUserId());
    }

    @GetMapping("/addresses")
    @Operation(summary = "List the user's saved addresses (default first, then newest).")
    List<SavedAddressDto> listAddresses() {
        return authService.listAddresses(currentUserId());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a saved address.")
    SavedAddressDto addAddress(@RequestBody AddressInput input) {
        return authService.addAddress(currentUserId(), input);
    }

    @PutMapping("/addresses/{id}")
    @Operation(summary = "Update one of the user's addresses (404 if not owned).")
    SavedAddressDto updateAddress(@PathVariable Long id, @RequestBody AddressInput input) {
        return authService.updateAddress(currentUserId(), id, input);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of the user's addresses (404 if not owned).")
    void deleteAddress(@PathVariable Long id) {
        authService.deleteAddress(currentUserId(), id);
    }

    /**
     * The authenticated caller's user id. The security config guarantees a valid
     * token reached these routes, so the principal is always a {@code Long}.
     */
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return userId;
    }
}
