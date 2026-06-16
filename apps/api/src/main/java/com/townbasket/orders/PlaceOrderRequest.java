package com.townbasket.orders;

import com.townbasket.payments.PaymentMethod;
import java.util.UUID;

/**
 * Checkout request body. {@code paymentMethod} is COD or UPI. The idempotency
 * key is supplied via the {@code Idempotency-Key} header (preferred) or this
 * optional field as a fallback.
 */
public record PlaceOrderRequest(
        UUID cartId,
        String customerName,
        String phone,
        AddressDto address,
        PaymentMethod paymentMethod,
        String idempotencyKey) {
}
