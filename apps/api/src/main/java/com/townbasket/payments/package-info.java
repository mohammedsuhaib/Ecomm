/**
 * {@code payments} module — UPI (Paytm PG) and Pay on Delivery behind a
 * {@code PaymentProvider} port (PaytmProvider + CodProvider + FakeProvider).
 *
 * <p>Payment state lives on the order as {@code paymentStatus}, separate from
 * the order status: every order is PLACED at checkout and staff confirm it
 * from the admin queue. Online (UPI) payment is server-verified (checksum
 * checked, idempotent webhook) before it is recorded PAID; unpaid online
 * orders auto-cancel after a timeout, releasing reserved stock. COD is
 * recorded as collected when the order is delivered.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payments")
package com.townbasket.payments;
