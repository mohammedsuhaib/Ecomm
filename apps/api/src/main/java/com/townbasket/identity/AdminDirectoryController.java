package com.townbasket.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/delivery-agents/{id}/active")
    @Operation(summary = "Activate or deactivate a delivery agent.")
    DeliveryAgentDto setDeliveryAgentActive(
            @PathVariable("id") Long id, @RequestBody SetActiveRequest request) {
        return authService.setDeliveryAgentActive(id, request.active());
    }
}
