package com.townbasket.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.identity.AuthService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A browser push subscription outlives the session that registered it, so the
 * only thing keeping a signed-out phone quiet is the session check in the
 * channel. Getting this wrong is not a cosmetic bug: a handed-back rider phone
 * would announce the previous rider's deliveries — their customers' addresses —
 * to whoever is holding it now.
 *
 * <p>Asserted by whether the subscriptions are looked up at all, which is the
 * observable difference: the signed-out case must return before the lookup, not
 * fetch rows and then decline to use them.
 */
class WebPushSessionGateTest {

    private static final Long RIDER = 7L;

    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final WebPushNotificationChannel channel = new WebPushNotificationChannel(
            new WebPushProperties("", "", null), subscriptions, authService, new ObjectMapper());

    @Test
    void signedOutRecipientIsNotPushedTo() {
        when(authService.hasActiveSession(RIDER)).thenReturn(false);

        assertThat(channel.send(assignment())).isFalse();

        verify(subscriptions, never()).findByUserId(any());
    }

    @Test
    void signedInRecipientIsPushedTo() {
        when(authService.hasActiveSession(RIDER)).thenReturn(true);
        when(subscriptions.findByUserId(RIDER)).thenReturn(List.of());

        // No subscriptions registered, so nothing is delivered — but the lookup
        // proves the session check let this one through.
        assertThat(channel.send(assignment())).isFalse();

        verify(subscriptions).findByUserId(RIDER);
    }

    @Test
    void staffQueueMessagesThatAreNotPushWorthyNeverTouchTheDatabaseOrASession() {
        // A confirmation or delivery only moves the live queue; no device is
        // buzzed for it.
        assertThat(channel.send(NotificationMessage.forAdmin(
                1L, "ORDER_CONFIRMED", "CONFIRMED", "Order confirmed", "Accepted"))).isFalse();

        verify(subscriptions, never()).findByAudience(any());
        verify(authService, never()).hasActiveSession(any());
    }

    @Test
    void newOrderIsNotPushedToAStaffDeviceWhoseOwnerHasSignedOut() {
        PushSubscriptionEntity tablet = staffDevice(5L);
        when(subscriptions.findByAudience("STAFF")).thenReturn(List.of(tablet));
        when(authService.hasActiveSession(5L)).thenReturn(false);

        assertThat(channel.send(newOrder())).isFalse();

        // Signed out: the device is skipped without a send, and the row stays.
        verify(subscriptions, never()).delete(any());
    }

    @Test
    void newOrderAsksAboutEachStaffAccountOnce() {
        when(subscriptions.findByAudience("STAFF"))
                .thenReturn(List.of(staffDevice(5L), staffDevice(5L), staffDevice(6L)));
        when(authService.hasActiveSession(5L)).thenReturn(false);
        when(authService.hasActiveSession(6L)).thenReturn(false);

        channel.send(newOrder());

        verify(authService, times(1)).hasActiveSession(5L);
        verify(authService, times(1)).hasActiveSession(6L);
    }

    @Test
    void noStaffDevicesMeansNothingToSend() {
        when(subscriptions.findByAudience("STAFF")).thenReturn(List.of());

        assertThat(channel.send(newOrder())).isFalse();

        verify(authService, never()).hasActiveSession(any());
    }

    private static NotificationMessage newOrder() {
        return NotificationMessage.forAdmin(
                1L, "ORDER_PLACED", "PLACED", "New order #ABC", "A new order just came in.");
    }

    private static PushSubscriptionEntity staffDevice(Long userId) {
        PushSubscriptionEntity e = new PushSubscriptionEntity(
                userId, "https://push.example/" + userId + "/" + System.nanoTime(), "p", "a", "JUnit");
        e.setAudience("STAFF");
        return e;
    }

    private static NotificationMessage assignment() {
        return NotificationMessage.forAgent(
                1L, RIDER, "ORDER_ASSIGNED", "OUT_FOR_DELIVERY", "New delivery", "12 MG Road");
    }
}
