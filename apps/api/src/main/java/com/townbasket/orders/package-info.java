/**
 * {@code orders} module — the order state machine
 * (PLACED → CONFIRMED → PACKING → READY_FOR_DELIVERY → OUT_FOR_DELIVERY →
 * DELIVERED, with CANCELLED). Staff drive it as far as READY_FOR_DELIVERY; the
 * hand-over to OUT_FOR_DELIVERY belongs to the rider who collects the bag.
 *
 * <p>Checkout is idempotent via a client idempotency key. Each transition emits
 * an event consumed by {@code inventory}, {@code payments} and
 * {@code notifications}. Prices (and COGS, when analytics is engaged) are
 * snapshotted on the order so catalog changes never mutate history.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Orders")
package com.townbasket.orders;
