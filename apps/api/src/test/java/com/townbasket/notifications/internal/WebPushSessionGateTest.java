package com.townbasket.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void staffQueueMessagesNeverAskAboutASession() {
        // They have no recipient — they travel by SSE to an authenticated page.
        assertThat(channel.send(NotificationMessage.forAdmin(
                1L, "ORDER_PLACED", "CONFIRMED", "New order", "A new order came in"))).isFalse();

        verify(authService, never()).hasActiveSession(any());
    }

    private static NotificationMessage assignment() {
        return NotificationMessage.forAgent(
                1L, RIDER, "ORDER_ASSIGNED", "OUT_FOR_DELIVERY", "New delivery", "12 MG Road");
    }
}
