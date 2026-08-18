package com.townbasket.orders;

import java.time.LocalDate;

/**
 * Admin reporting row: how many orders an agent delivered on a given date
 * (from the order-events audit trail). Only DELIVERED orders with an assigned
 * agent are counted.
 */
public record AgentDeliveryStat(Long agentId, LocalDate date, long deliveries) {
}
