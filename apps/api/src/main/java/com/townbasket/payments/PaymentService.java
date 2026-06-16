package com.townbasket.payments;

import java.math.BigDecimal;

/**
 * Published API of the payments module, used synchronously by the {@code orders}
 * checkout. Dispatches to the right {@code PaymentProvider} (COD / UPI) behind
 * the scenes, records a {@code payments.payments} row, and returns the outcome.
 */
public interface PaymentService {

    /**
     * Charge an order via the chosen method.
     *
     * <ul>
     *   <li>{@link PaymentMethod#COD} -> records COD_PENDING (collected on delivery).</li>
     *   <li>{@link PaymentMethod#UPI} -> the active UPI provider charges; in M3/test
     *       the {@code FakeProvider} deterministically succeeds (PAID).</li>
     * </ul>
     */
    PaymentResult charge(Long orderId, PaymentMethod method, BigDecimal amount);
}
