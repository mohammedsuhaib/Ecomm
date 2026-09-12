package com.townbasket.serviceability;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store settings for staff under {@code /api/v1/admin/store} — secured to
 * STORE_STAFF/ADMIN by the {@code /api/v1/admin/**} rule in SecurityConfig.
 *
 * <p>Hours, minimum order, radius and location used to live only in SQL. The
 * "close for today" switch is the operational one: a festival or a power cut
 * should not require a database client.
 */
@RestController
@RequestMapping("/api/v1/admin/store")
@Tag(name = "Admin · Store", description = "Store hours, minimum order, delivery radius and manual closure.")
class AdminStoreController {

    private final ServiceabilityService serviceabilityService;

    AdminStoreController(ServiceabilityService serviceabilityService) {
        this.serviceabilityService = serviceabilityService;
    }

    @GetMapping
    @Operation(summary = "Current store settings, including whether it is open right now and any manual closure.")
    StoreDto get() {
        return serviceabilityService.activeStore()
                .orElseThrow(() -> new IllegalStateException("No active store configured"));
    }

    @PutMapping
    @Operation(summary = "Replace the store's operating settings.")
    StoreDto update(@RequestBody StoreUpdateRequest request) {
        return serviceabilityService.updateStore(request);
    }

    @PostMapping("/close-today")
    @Operation(summary = "Close for the rest of today (store time) with an optional reason; lapses at midnight.")
    StoreDto closeToday(@RequestBody(required = false) CloseStoreRequest request) {
        return serviceabilityService.closeForToday(request == null ? null : request.reason());
    }

    @PostMapping("/reopen")
    @Operation(summary = "Lift a manual closure now.")
    StoreDto reopen() {
        return serviceabilityService.reopen();
    }
}
