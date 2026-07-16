package com.townbasket.analytics;

import java.util.List;

/** Published API of the analytics module. All queries are read-only. */
public interface AnalyticsService {

    /** Today's GMV, order counts, and pending queue depth for a store. */
    AnalyticsSummaryDto getSummary(Long storeId);

    /** Revenue, order count, and gross profit per calendar day (IST) for the last {@code days} days. */
    List<DailySummaryDto> getDailySummary(Long storeId, int days);

    /** Top {@code limit} variants by units sold in the last {@code days} days. */
    List<TopProductDto> getTopProducts(Long storeId, int days, int limit);

    /** All variants at or below their low-stock threshold for a store. */
    List<LowStockDto> getLowStockItems(Long storeId);
}
