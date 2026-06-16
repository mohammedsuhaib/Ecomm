package com.townbasket.serviceability;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serviceability + store REST API under {@code /api/v1}. Part of the
 * serviceability module's published surface.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Serviceability", description = "Delivery-radius check and store details.")
class ServiceabilityController {

    private final ServiceabilityService serviceabilityService;

    ServiceabilityController(ServiceabilityService serviceabilityService) {
        this.serviceabilityService = serviceabilityService;
    }

    @GetMapping("/serviceability/check")
    @Operation(summary = "Check if a lat/lng is within the active store's delivery radius (Haversine).")
    ServiceabilityCheckDto check(@RequestParam double lat, @RequestParam double lng) {
        return serviceabilityService.check(lat, lng);
    }

    @GetMapping("/store")
    @Operation(summary = "Get the active store's public details.")
    ResponseEntity<StoreDto> store() {
        return serviceabilityService.activeStore()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
