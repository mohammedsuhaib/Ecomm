/**
 * {@code payments} module — UPI (Paytm PG) and Cash on Delivery behind a
 * {@code PaymentProvider} port (PaytmProvider + CodProvider + FakeProvider).
 *
 * <p>Online (UPI) orders reach CONFIRMED only after server-verified payment
 * success (checksum-verified, idempotent webhook); unpaid online orders
 * auto-cancel after a timeout, releasing reserved stock. COD orders are
 * confirmed at placement and payment is recorded as collected on delivery.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payments")
package com.townbasket.payments;
