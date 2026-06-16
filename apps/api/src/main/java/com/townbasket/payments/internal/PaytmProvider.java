package com.townbasket.payments.internal;

import com.townbasket.payments.PaymentMethod;
import java.math.BigDecimal;

/**
 * Live UPI provider (Paytm Payment Gateway).
 *
 * <p><strong>TODO (M5):</strong> implement the real Paytm PG flow — create a
 * payment order, redirect the customer to complete UPI, then confirm only after
 * a checksum-verified, idempotent webhook/callback reports success (never off a
 * client-side callback alone), emitting {@code PaymentSucceeded}/{@code PaymentFailed};
 * unpaid online orders auto-cancel after a timeout and release reserved stock
 * (ARCHITECTURE §3.6).
 *
 * <p>Deliberately NOT a Spring bean yet (no {@code @Component}) and not wired in,
 * so the {@code FakeProvider} remains the active UPI provider for M3. Do not
 * implement Paytm in M3.
 */
class PaytmProvider implements PaymentProvider {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.UPI;
    }

    @Override
    public Charge charge(Long orderId, BigDecimal amount) {
        throw new UnsupportedOperationException("PaytmProvider is implemented in M5");
    }
}
