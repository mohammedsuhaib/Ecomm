package com.townbasket.notifications.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Browser push channel (Web Push / RFC 8291 + VAPID): reaches the customer even
 * when the storefront PWA is closed. Encrypted per subscription with the keys
 * the browser minted, and signed with this server's VAPID key pair.
 *
 * <p>Disabled unless a VAPID key pair is configured, so local development and
 * any deployment that hasn't set the keys behaves exactly as before.
 *
 * <p>Self-healing: a push service that answers 404/410 is telling us the
 * subscription is permanently gone (app uninstalled, site data cleared), so the
 * row is deleted rather than retried forever.
 */
@Component
@EnableConfigurationProperties(WebPushProperties.class)
class WebPushNotificationChannel implements NotificationChannel {

    static final String NAME = "WEB_PUSH";

    private static final Logger log = LoggerFactory.getLogger(WebPushNotificationChannel.class);

    /** Push services drop an undelivered message after this many seconds. */
    private static final int TTL_SECONDS = 6 * 60 * 60;

    private final WebPushProperties properties;
    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper;

    /** Built once at startup; null when the channel is not configured. */
    private PushService pushService;

    WebPushNotificationChannel(WebPushProperties properties,
                               PushSubscriptionRepository subscriptions,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (!properties.configured()) {
            log.info("Web Push channel disabled — no VAPID key pair configured "
                    + "(set townbasket.notifications.push.public-key/private-key to enable).");
            return;
        }
        // web-push encrypts with BouncyCastle primitives and expects the
        // application to have registered the provider.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            pushService = new PushService(properties.publicKey(), properties.privateKey(), properties.subject());
            log.info("Web Push channel enabled.");
        } catch (Exception e) {
            // A malformed key pair must not stop the application from booting —
            // every other channel still works.
            log.error("Web Push channel could not start; check the VAPID key pair: {}", e.toString());
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return pushService != null;
    }

    @Override
    @Transactional
    public boolean send(NotificationMessage message) {
        // Staff-queue messages have no customer recipient; they travel by SSE.
        if (message.recipientUserId() == null) {
            return false;
        }
        List<PushSubscriptionEntity> targets = subscriptions.findByUserId(message.recipientUserId());
        if (targets.isEmpty()) {
            return false; // customer never granted permission — not a failure
        }
        byte[] payload = payload(message);
        int delivered = 0;
        for (PushSubscriptionEntity subscription : targets) {
            if (deliver(subscription, payload)) {
                delivered++;
            }
        }
        return delivered > 0;
    }

    /** Send to one subscription; prune it when the push service says it is gone. */
    private boolean deliver(PushSubscriptionEntity subscription, byte[] payload) {
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    payload,
                    TTL_SECONDS);
            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                subscriptions.delete(subscription);
                return false;
            }
            if (status >= 400) {
                log.warn("Push to subscription {} rejected with HTTP {}", subscription.getId(), status);
                return false;
            }
            subscription.setLastSentAt(Instant.now());
            subscriptions.save(subscription);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            // Best-effort: a single unreachable push service must not break the rest.
            log.warn("Push to subscription {} failed: {}", subscription.getId(), e.toString());
            return false;
        }
    }

    /** The JSON the service worker's {@code push} handler reads. */
    private byte[] payload(NotificationMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", message.title());
        body.put("body", message.body());
        body.put("url", message.url());
        body.put("orderId", message.orderId());
        body.put("type", message.type());
        body.put("status", message.status());
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            // Cannot happen for a plain map, but never let it reach the caller.
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
