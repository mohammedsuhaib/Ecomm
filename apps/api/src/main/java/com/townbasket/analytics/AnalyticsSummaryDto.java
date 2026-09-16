package com.townbasket.analytics;

import java.math.BigDecimal;

/**
 * Aggregate snapshot returned by GET /admin/analytics/summary: strictly the
 * figures that are about TODAY or about right now.
 *
 * <p>Anything spanning a range belongs to {@code /daily}, which takes the
 * dashboard's selected period. This record used to carry a fixed 7-day revenue
 * and order count as well, which the dashboard rendered as a "Week Revenue"
 * tile beside a 7/30/90-day filter that could not move it — so staff switching
 * to 90 days watched a "7 days" figure stay put and reasonably read the whole
 * filter as broken. The period tiles are now summed from the daily series,
 * which the filter does drive.
 */
public record AnalyticsSummaryDto(
        BigDecimal todayRevenue,
        int todayOrders,
        int todayDelivered,
        int pendingOrders) {}
