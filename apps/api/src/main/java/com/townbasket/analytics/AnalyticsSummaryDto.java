package com.townbasket.analytics;

import java.math.BigDecimal;

/** Aggregate snapshot returned by GET /admin/analytics/summary. */
public record AnalyticsSummaryDto(
        BigDecimal todayRevenue,
        int todayOrders,
        int todayDelivered,
        int pendingOrders,
        BigDecimal weekRevenue,
        int weekOrders) {}
