package com.townbasket.payments;

import java.math.BigDecimal;
import java.util.List;

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

    /**
     * The methods a customer may actually choose on this deployment, in display
     * order. Pay on Delivery is always present; online UPI appears only once a
     * real gateway is configured.
     */
    List<PaymentMethod> enabledMethods();

    /**
     * Reject a method this deployment does not accept.
     *
     * @throws com.townbasket.shared.BusinessRuleException if the method is off
     */
    void requireEnabled(PaymentMethod method);
}
