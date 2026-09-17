package com.townbasket.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin staff/agent directory under {@code /api/v1/admin} (secured to
 * {@code STORE_STAFF | ADMIN}). Exposes delivery agents for order dispatch, plus
 * onboarding/activation for the rider-management panel.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Directory", description = "Staff/agent directory for order dispatch and rider management.")
class AdminDirectoryController {

    private final AuthService authService;

    AdminDirectoryController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/delivery-agents")
    @Operation(summary = "List delivery agents (active-only by default; includeInactive=true for the full roster).")
    List<DeliveryAgentDto> deliveryAgents(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return authService.listDeliveryAgents(includeInactive);
    }

    @PostMapping("/delivery-agents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Onboard a delivery agent (email + password login).")
    DeliveryAgentDto createDeliveryAgent(@RequestBody CreateDeliveryAgentRequest request) {
        return authService.createDeliveryAgent(request);
    }

    /** ADMIN only (SecurityConfig rule on /admin/staff). */
    @GetMapping("/staff")
    @Operation(summary = "List staff and admin accounts (ADMIN only).")
    List<StaffMemberDto> staff() {
        return authService.listStaff();
    }

    /**
     * Create a STORE_STAFF or ADMIN account. ADMIN only, by the same
     * SecurityConfig rule on {@code /admin/staff} that guards the listing —
     * handing out a login to the admin surface is not a store-staff power.
     *
     * <p>Riders go through {@code POST /delivery-agents} above: they carry an
     * on-duty lifecycle this path does not set up.
     */
    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a staff or admin account (ADMIN only).")
    StaffMemberDto createStaff(@RequestBody CreateStaffRequest request) {
        return authService.createStaff(request);
    }

    /**
     * Set a new password for a rider or staff member who has forgotten theirs,
     * signing out all their sessions. Who may reset whom is decided in the
     * service from the caller's stored role (ADMIN → staff/riders, STORE_STAFF
     * → riders only); the route itself is open to both roles.
     */
    @PostMapping("/users/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reset another account's password (ADMIN: staff + riders; STORE_STAFF: riders).")
    void resetPassword(@PathVariable("id") Long id, @RequestBody ResetPasswordRequest request,
                       @AuthenticationPrincipal Long callerId) {
        authService.resetPassword(callerId, id, request == null ? null : request.newPassword());
    }

    @PostMapping("/delivery-agents/{id}/active")
    @Operation(summary = "Activate or deactivate a delivery agent.")
    DeliveryAgentDto setDeliveryAgentActive(
            @PathVariable("id") Long id, @RequestBody SetActiveRequest request) {
        return authService.setDeliveryAgentActive(id, request.active());
    }
}
