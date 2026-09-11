package com.townbasket.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Admin reporting row: how many orders an agent delivered on a given date and
 * their summed order value in rupees, tax-inclusive (from the order-events
 * audit trail). Only DELIVERED orders with an assigned agent are counted,
 * each order at most once. The metric is order value by <em>delivery</em>
 * date — deliberately not the analytics module's daily revenue, which buckets
 * by placed-at (IST) and excludes CANCELLED orders.
 */
public record AgentDeliveryStat(Long agentId, LocalDate date, long deliveries, BigDecimal amount) {
}
