package com.townbasket.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Admin reporting row: how many orders an agent delivered on a given date and
 * their summed order value in rupees, tax-inclusive (from the order-events
 * audit trail). Only DELIVERED orders with an assigned agent are counted.
 */
public record AgentDeliveryStat(Long agentId, LocalDate date, long deliveries, BigDecimal amount) {
}
