package com.townbasket.payments.internal;

import com.townbasket.payments.PaymentMethod;
import com.townbasket.payments.PaymentStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Cash on Delivery provider. No prepayment: the order is confirmed at placement
 * and cash is collected when the order is marked delivered. Records COD_PENDING.
 */
@Component
class CodProvider implements PaymentProvider {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.COD;
    }

    @Override
    public Charge charge(Long orderId, BigDecimal amount) {
        return new Charge(PaymentStatus.COD_PENDING, null);
    }
}
