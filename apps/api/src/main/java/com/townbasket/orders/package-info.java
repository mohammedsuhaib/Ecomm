/**
 * {@code orders} module — the staff-driven order state machine
 * (PLACED → CONFIRMED → PACKING → OUT_FOR_DELIVERY → DELIVERED, with CANCELLED).
 *
 * <p>Checkout is idempotent via a client idempotency key. Each transition emits
 * an event consumed by {@code inventory}, {@code payments} and
 * {@code notifications}. Prices (and COGS, when analytics is engaged) are
 * snapshotted on the order so catalog changes never mutate history.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Orders")
package com.townbasket.orders;
