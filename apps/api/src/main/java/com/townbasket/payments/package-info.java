/**
 * {@code payments} module — online UPI payments via Paytm PG behind a
 * {@code PaymentProvider} port (PaytmProvider + FakeProvider).
 *
 * <p>Orders reach CONFIRMED only after server-verified payment success
 * (checksum-verified, idempotent webhook). Unpaid orders auto-cancel after a
 * timeout, releasing reserved stock. No Cash on Delivery (deliberate product
 * exclusion).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payments")
package com.townbasket.payments;
