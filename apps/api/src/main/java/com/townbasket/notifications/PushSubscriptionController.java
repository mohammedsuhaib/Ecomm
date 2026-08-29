package com.townbasket.notifications;

import com.townbasket.notifications.internal.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web Push subscription management for the storefront PWA.
 *
 * <p>{@code GET /notifications/push-config} is public so the UI can decide
 * whether to offer the opt-in at all. Subscribing requires a logged-in customer
 * (the subscription is stored against their account so order updates reach the
 * right person). Unsubscribing is authorised by the endpoint itself — an
 * unguessable URL minted by the browser's push service — so it still works from
 * a signed-out browser that is cleaning up after itself.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Web Push subscriptions for order updates.")
class PushSubscriptionController {

    private final PushSubscriptionService service;

    PushSubscriptionController(PushSubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/push-config")
    @Operation(summary = "VAPID public key + whether push is available on this deployment.")
    PushConfigDto pushConfig() {
        String key = service.vapidPublicKey();
        return new PushConfigDto(key != null, key);
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Register this browser to receive order-update notifications.")
    void subscribe(@RequestBody PushSubscriptionRequest request,
                   @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        service.subscribe(currentUserId(), request, userAgent);
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stop sending notifications to this browser.")
    void unsubscribe(@RequestBody PushSubscriptionRequest request) {
        service.unsubscribe(request == null ? null : request.endpoint());
    }

    /**
     * The authenticated caller's user id. The security config guarantees a valid
     * token reached this route, so the principal is always a {@code Long}.
     */
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return userId;
    }
}
