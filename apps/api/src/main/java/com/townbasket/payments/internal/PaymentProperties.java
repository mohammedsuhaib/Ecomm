package com.townbasket.payments.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which payment methods this deployment actually accepts.
 *
 * <p>Pay on Delivery is always available. Online UPI stays OFF until a real
 * gateway is integrated and its keys are configured — the current UPI provider
 * is a fake that auto-succeeds, so letting a customer choose it would mark an
 * order PAID with no money received. Flip it with
 * {@code UPI_ENABLED=true} once a live gateway is wired up.
 *
 * @param upiEnabled whether customers may choose online UPI
 */
@ConfigurationProperties(prefix = "townbasket.payments")
record PaymentProperties(boolean upiEnabled) {
}
