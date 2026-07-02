package com.townbasket.analytics;

import java.math.BigDecimal;

/** Per-day revenue, order count, and gross profit for the daily chart. */
public record DailySummaryDto(
        String date,
        BigDecimal revenue,
        int orders,
        BigDecimal grossProfit) {}
