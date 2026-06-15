/**
 * {@code inventory} module — stock per {@code (store_id, variant_id)} with
 * {@code on_hand} and {@code reserved}, a stock-movement audit ledger and
 * reservations.
 *
 * <p>Checkout reserves stock atomically; reservations commit on order
 * confirmation or release on cancellation/payment timeout. Emits
 * {@code StockLow} / {@code StockChanged} events.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Inventory")
package com.townbasket.inventory;
