package com.townbasket.notifications.internal;

import com.townbasket.notifications.PushSubscriptionRequest;
import com.townbasket.shared.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers and removes the browser push subscriptions the storefront creates
 * when a customer allows notifications. Module-internal implementation behind
 * the module's own controller.
 */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository subscriptions;
    private final WebPushProperties properties;

    PushSubscriptionService(PushSubscriptionRepository subscriptions, WebPushProperties properties) {
        this.subscriptions = subscriptions;
        this.properties = properties;
    }

    /**
     * The VAPID public key browsers need to create a subscription bound to this
     * server, or {@code null} when push is not configured.
     */
    public String vapidPublicKey() {
        return properties.configured() ? properties.publicKey() : null;
    }

    /**
     * Store (or re-point) a subscription for the calling user.
     *
     * <p>Idempotent on {@code endpoint}: a browser hands back the same endpoint
     * every time until it rotates, and the same device can be re-subscribed by a
     * different account (a shared phone), so an existing row is updated in place
     * rather than duplicated — the UNIQUE constraint on endpoint depends on it.
     */
    @Transactional
    public void subscribe(Long userId, PushSubscriptionRequest request, String userAgent) {
        if (request == null || isBlank(request.endpoint())) {
            throw new BusinessRuleException("A push subscription endpoint is required.");
        }
        if (request.keys() == null || isBlank(request.keys().p256dh()) || isBlank(request.keys().auth())) {
            throw new BusinessRuleException("A push subscription must carry its p256dh and auth keys.");
        }
        String endpoint = request.endpoint().trim();
        PushSubscriptionEntity entity = subscriptions.findByEndpoint(endpoint).orElse(null);
        if (entity == null) {
            subscriptions.save(new PushSubscriptionEntity(
                    userId, endpoint, request.keys().p256dh().trim(), request.keys().auth().trim(), userAgent));
            return;
        }
        entity.setUserId(userId);
        entity.setP256dh(request.keys().p256dh().trim());
        entity.setAuth(request.keys().auth().trim());
        entity.setUserAgent(userAgent);
        subscriptions.save(entity);
    }

    /**
     * Remove a subscription by its endpoint. The endpoint is an unguessable
     * capability URL minted by the browser's push service, so knowing it is
     * sufficient authority to cancel it — the same capability model the order
     * tracking token uses. Silent when it is already gone (idempotent).
     */
    @Transactional
    public void unsubscribe(String endpoint) {
        if (isBlank(endpoint)) {
            throw new BusinessRuleException("A push subscription endpoint is required.");
        }
        subscriptions.deleteByEndpoint(endpoint.trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
