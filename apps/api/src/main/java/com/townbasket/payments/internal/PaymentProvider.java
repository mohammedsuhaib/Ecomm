package com.townbasket.payments.internal;

import com.townbasket.payments.PaymentMethod;
import com.townbasket.payments.PaymentStatus;
import java.math.BigDecimal;

/**
 * Port for a payment method (ARCHITECTURE §3.6). Implementations: {@code CodProvider},
 * {@code FakeProvider} (test/M3 UPI), and {@code PaytmProvider} (live UPI, M5).
 * Keeping each method behind this port makes vendors independently swappable.
 */
interface PaymentProvider {

    /** The method this provider handles. */
    PaymentMethod method();

    /**
     * Attempt the charge and return the resulting status plus a provider
     * reference (nullable). Implementations must be safe to call once per order
     * at checkout time.
     */
    Charge charge(Long orderId, BigDecimal amount);

    /** Provider charge outcome: a status and an optional transaction reference. */
    record Charge(PaymentStatus status, String reference) {
    }
}
