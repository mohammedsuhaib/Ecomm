package com.townbasket.payments.internal;

import com.townbasket.payments.PaymentMethod;
import com.townbasket.payments.PaymentStatus;
import java.math.BigDecimal;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Fake UPI provider for tests / local / the M3 demo: deterministically succeeds
 * (PAID) with a synthetic reference, so a full UPI checkout can be exercised
 * without Paytm. Marked {@link Primary} so it is the active UPI provider until
 * the live {@code PaytmProvider} replaces it in M5.
 */
@Component
@Primary
class FakeProvider implements PaymentProvider {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.UPI;
    }

    @Override
    public Charge charge(Long orderId, BigDecimal amount) {
        return new Charge(PaymentStatus.PAID, "FAKE-UPI-" + orderId);
    }
}
