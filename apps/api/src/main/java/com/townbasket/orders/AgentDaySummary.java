package com.townbasket.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A rider's own day at a glance: how many orders they delivered on
 * {@code date} (the store's calendar day, IST) and how much cash they took at
 * the door doing it. Only Pay-on-Delivery orders contribute to
 * {@code codCollected} — a UPI order was paid before the rider ever saw it, so
 * it is counted in {@code deliveredCount} but not in the money. Amounts are
 * order totals in rupees, tax-inclusive, exactly what the rider was told to
 * collect on the card.
 *
 * <p>Each order counts once even if the DELIVERED transition was committed
 * twice by a double-tap (see the repository query), and an order's delivery
 * is attributed to the rider it was assigned to at the time — assignment cannot
 * change after DELIVERED.
 */
public record AgentDaySummary(
        LocalDate date,
        long deliveredCount,
        long codOrders,
        BigDecimal codCollected) {
}
