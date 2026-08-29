package com.townbasket.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.notifications.internal.PushSubscriptionService;
import com.townbasket.shared.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Web Push subscription lifecycle against a real Postgres: register, re-register
 * the same browser (must update in place, not duplicate — the endpoint is
 * UNIQUE), hand a device over to another account, and unsubscribe.
 */
class PushSubscriptionIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/test-abc123";

    @Autowired
    PushSubscriptionService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void subscribeIsIdempotentPerEndpointAndUnsubscribeRemovesIt() {
        service.subscribe(1L, request(ENDPOINT, "key-one", "auth-one"), "JUnit/1.0");
        assertThat(countFor(ENDPOINT)).isEqualTo(1);
        assertThat(ownerOf(ENDPOINT)).isEqualTo(1L);

        // The browser hands back the same endpoint on every visit; re-subscribing
        // must refresh the row rather than violate the UNIQUE constraint.
        service.subscribe(1L, request(ENDPOINT, "key-two", "auth-two"), "JUnit/1.1");
        assertThat(countFor(ENDPOINT)).isEqualTo(1);

        // A shared device signed into a different account re-points the same
        // subscription, so updates never reach the previous owner.
        service.subscribe(2L, request(ENDPOINT, "key-two", "auth-two"), "JUnit/1.1");
        assertThat(countFor(ENDPOINT)).isEqualTo(1);
        assertThat(ownerOf(ENDPOINT)).isEqualTo(2L);

        service.unsubscribe(ENDPOINT);
        assertThat(countFor(ENDPOINT)).isZero();

        // Unsubscribing something already gone is a no-op, not an error: the
        // browser may clean up after the server already pruned a dead endpoint.
        service.unsubscribe(ENDPOINT);
        assertThat(countFor(ENDPOINT)).isZero();
    }

    @Test
    void rejectsSubscriptionsMissingEndpointOrKeys() {
        assertThatThrownBy(() -> service.subscribe(1L, request(null, "k", "a"), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.subscribe(1L, request("https://push/x", null, "a"), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.subscribe(1L, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void vapidKeyIsAbsentWhenPushIsNotConfigured() {
        // Tests run without VAPID keys, so the storefront is told push is
        // unavailable and hides the opt-in instead of failing on subscribe.
        assertThat(service.vapidPublicKey()).isNull();
    }

    private static PushSubscriptionRequest request(String endpoint, String p256dh, String auth) {
        return new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys(p256dh, auth));
    }

    private Integer countFor(String endpoint) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications.push_subscriptions WHERE endpoint = ?",
                Integer.class, endpoint);
    }

    private Long ownerOf(String endpoint) {
        return jdbc.queryForObject(
                "SELECT user_id FROM notifications.push_subscriptions WHERE endpoint = ?",
                Long.class, endpoint);
    }
}
