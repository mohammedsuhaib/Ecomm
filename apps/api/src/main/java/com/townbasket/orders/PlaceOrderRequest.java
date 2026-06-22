package com.townbasket.orders;

import com.townbasket.payments.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Checkout request body. {@code paymentMethod} is COD or UPI. The idempotency
 * key is supplied via the {@code Idempotency-Key} header (preferred) or this
 * optional field as a fallback.
 *
 * <p>{@code expectedTotal} is the subtotal the customer was shown on the
 * checkout button. When present, the server rejects the order if the live
 * re-priced subtotal differs, so a price change between cart load and submit
 * never silently charges a different amount. Optional (skipped when null).
 */
public record PlaceOrderRequest(
        UUID cartId,
        String customerName,
        String phone,
        AddressDto address,
        PaymentMethod paymentMethod,
        BigDecimal expectedTotal,
        String idempotencyKey) {
}
