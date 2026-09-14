package com.townbasket.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.townbasket.notifications.internal.NotificationMessage.Audience;
import com.townbasket.shared.events.OrderAssigned;
import com.townbasket.shared.events.OrderStatusChanged;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Who gets told what. These rules are the whole point of the module and are
 * easy to get subtly wrong — a rider buzzed about a job they cannot start yet,
 * a customer's phone buzzing twice for one transition, or a rider's delivery
 * address leaking into the customer's live tracking stream.
 */
class NotificationRoutingTest {

    private static final Long AGENT = 7L;
    private static final Long OTHER_AGENT = 9L;
    private static final Long CUSTOMER = 42L;
    /** The customer-facing order code that notification text must quote. */
    private static final String CODE = "7K4M2QX9";

    private RecordingChannel channel;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        channel = new RecordingChannel();
        listener = new NotificationEventListener(
                List.of(channel), mock(NotificationLogRepository.class));
    }

    @Test
    void assigningAWorkInProgressOrderDoesNotBuzzTheRider() {
        // Orders are normally assigned while still being packed. The rider's
        // queue only lists OUT_FOR_DELIVERY orders, so notifying now would point
        // them at a job they cannot see or collect.
        listener.on(new OrderAssigned(1L, CODE, 1L, AGENT, null, "PACKING", "12 MG Road"));

        assertThat(channel.messages).isEmpty();
    }

    @Test
    void assigningAnOrderAlreadyOutForDeliveryBuzzesTheRiderWithTheAddress() {
        listener.on(new OrderAssigned(1L, CODE, 1L, AGENT, null, "OUT_FOR_DELIVERY", "12 MG Road"));

        assertThat(channel.messages).hasSize(1);
        NotificationMessage message = channel.messages.get(0);
        assertThat(message.audience()).isEqualTo(Audience.AGENT);
        assertThat(message.recipientUserId()).isEqualTo(AGENT);
        assertThat(message.type()).isEqualTo("ORDER_ASSIGNED");
        assertThat(message.body()).contains("12 MG Road");
    }

    @Test
    void handingAnOrderToAnotherRiderTellsBothOfThem() {
        listener.on(new OrderAssigned(1L, CODE, 1L, OTHER_AGENT, AGENT, "OUT_FOR_DELIVERY", "12 MG Road"));

        assertThat(channel.messages).hasSize(2);
        assertThat(channel.messages).allSatisfy(m -> assertThat(m.audience()).isEqualTo(Audience.AGENT));
        assertThat(channel.messages).extracting(NotificationMessage::recipientUserId)
                .containsExactlyInAnyOrder(OTHER_AGENT, AGENT);
        // The rider who lost it must be told, or they set off for a job that
        // is no longer theirs.
        assertThat(channel.messages).extracting(NotificationMessage::type)
                .contains("ORDER_UNASSIGNED");
    }

    @Test
    void losingAJobIsAlwaysWorthTellingEvenBeforeDispatch() {
        // Unlike gaining a job, this matters at any status.
        listener.on(new OrderAssigned(1L, CODE, 1L, null, AGENT, "PACKING", "12 MG Road"));

        assertThat(channel.messages).hasSize(1);
        assertThat(channel.messages.get(0).recipientUserId()).isEqualTo(AGENT);
        assertThat(channel.messages.get(0).type()).isEqualTo("ORDER_UNASSIGNED");
    }

    @Test
    void dispatchNotifiesTheCustomerAndTheAssignedRiderSeparately() {
        listener.on(statusChange("OUT_FOR_DELIVERY", AGENT));

        assertThat(channel.messages).hasSize(2);
        assertThat(channel.messages).extracting(NotificationMessage::audience)
                .containsExactly(Audience.CUSTOMER, Audience.AGENT);
        assertThat(channel.messages).extracting(NotificationMessage::recipientUserId)
                .containsExactly(CUSTOMER, AGENT);
    }

    @Test
    void cancellingReachesTheRiderCarryingTheOrder() {
        listener.on(statusChange("CANCELLED", AGENT));

        assertThat(channel.messages).hasSize(2);
        NotificationMessage riderMessage = channel.messages.get(1);
        assertThat(riderMessage.audience()).isEqualTo(Audience.AGENT);
        assertThat(riderMessage.recipientUserId()).isEqualTo(AGENT);
        assertThat(riderMessage.type()).isEqualTo("ORDER_CANCELLED");
    }

    @Test
    void middleOfTheFlowTransitionsOnlyReachTheCustomer() {
        // Packing changes nothing for the rider — don't buzz them for it.
        listener.on(statusChange("PACKING", AGENT));

        assertThat(channel.messages).hasSize(1);
        assertThat(channel.messages.get(0).audience()).isEqualTo(Audience.CUSTOMER);
    }

    @Test
    void unassignedOrdersProduceNoRiderMessage() {
        listener.on(statusChange("OUT_FOR_DELIVERY", null));

        assertThat(channel.messages).hasSize(1);
        assertThat(channel.messages.get(0).audience()).isEqualTo(Audience.CUSTOMER);
    }

    @Test
    void theLiveStreamChannelIgnoresRiderMessages() {
        // A rider's job — including the customer's address — must never be
        // replayed into the public order-tracking stream.
        SseNotificationChannel sse = new SseNotificationChannel(new SseRegistry());

        boolean riderSent = sse.send(NotificationMessage.forAgent(
                1L, AGENT, "ORDER_ASSIGNED", "OUT_FOR_DELIVERY", "New delivery", "12 MG Road"));
        boolean customerSent = sse.send(NotificationMessage.forCustomer(
                1L, CUSTOMER, "STATUS_CHANGED", "PACKING", "Packing", "…", "/order/tok"));

        assertThat(riderSent).isFalse();
        assertThat(customerSent).isTrue();
    }

    private static OrderStatusChanged statusChange(String to, Long agentId) {
        return new OrderStatusChanged(1L, CODE, 1L, "CONFIRMED", to, CUSTOMER, "tok-123", agentId, "12 MG Road");
    }

    /** Captures what the dispatcher hands to a channel, in order. */
    private static final class RecordingChannel implements NotificationChannel {
        private final List<NotificationMessage> messages = new ArrayList<>();

        @Override
        public String name() {
            return "RECORDING";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean send(NotificationMessage message) {
            messages.add(message);
            return true;
        }
    }
}
