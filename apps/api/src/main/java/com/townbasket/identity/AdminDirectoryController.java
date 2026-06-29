package com.townbasket.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin staff/agent directory under {@code /api/v1/admin} (secured to
 * {@code STORE_STAFF | ADMIN}). Currently exposes the active delivery agents so
 * the admin order queue can dispatch orders to a rider.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Directory", description = "Staff/agent directory for order dispatch.")
class AdminDirectoryController {

    private final AuthService authService;

    AdminDirectoryController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/delivery-agents")
    @Operation(summary = "List active delivery agents (for assigning orders).")
    List<UserDto> deliveryAgents() {
        return authService.listDeliveryAgents();
    }
}
