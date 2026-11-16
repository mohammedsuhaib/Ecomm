package com.townbasket.analytics;

/**
 * How many customers the shop has, returned by GET /admin/analytics/customers.
 *
 * <p>A customer is created the first time someone signs in with their phone, so
 * {@code total} counts everyone who has ever logged in — not everyone who has
 * bought something.
 *
 * @param total           customer accounts that exist
 * @param newInPeriod     accounts created in the last {@code days} days (IST days)
 * @param withOrders      customers with at least one order that was not cancelled
 * @param repeatCustomers customers with two or more such orders
 */
public record CustomerStatsDto(
        int total,
        int newInPeriod,
        int withOrders,
        int repeatCustomers) {}
