package com.townbasket.analytics.internal;

import com.townbasket.analytics.AnalyticsService;
import com.townbasket.analytics.AnalyticsSummaryDto;
import com.townbasket.analytics.DailySummaryDto;
import com.townbasket.analytics.LowStockDto;
import com.townbasket.analytics.TopProductDto;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analytics queries via native SQL across {@code orders.*}, {@code inventory.*},
 * and {@code catalog.*} schemas. Uses {@link NamedParameterJdbcTemplate} directly
 * — this is a read-only reporting module, intentionally bypassing JPA entity
 * boundaries for performance.
 */
@Service
@Transactional(readOnly = true)
class AnalyticsServiceImpl implements AnalyticsService {

    private final NamedParameterJdbcTemplate jdbc;

    AnalyticsServiceImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AnalyticsSummaryDto getSummary(Long storeId) {
        // Single-row conditional aggregation; FILTER avoids extra round-trips.
        // IST (Asia/Kolkata = UTC+5:30) boundary for "today".
        String sql = """
                SELECT
                  COUNT(*) FILTER (
                    WHERE status != 'CANCELLED'
                    AND (placed_at AT TIME ZONE 'Asia/Kolkata')::date
                      = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')::date
                  ) AS today_orders,
                  COALESCE(SUM(total) FILTER (
                    WHERE status != 'CANCELLED'
                    AND (placed_at AT TIME ZONE 'Asia/Kolkata')::date
                      = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')::date
                  ), 0) AS today_revenue,
                  COUNT(*) FILTER (
                    WHERE status = 'DELIVERED'
                    AND (placed_at AT TIME ZONE 'Asia/Kolkata')::date
                      = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')::date
                  ) AS today_delivered,
                  COUNT(*) FILTER (
                    WHERE status IN ('PLACED','CONFIRMED','PACKING','OUT_FOR_DELIVERY')
                  ) AS pending_orders,
                  COUNT(*) FILTER (
                    WHERE status != 'CANCELLED'
                    AND placed_at >= NOW() - INTERVAL '7 days'
                  ) AS week_orders,
                  COALESCE(SUM(total) FILTER (
                    WHERE status != 'CANCELLED'
                    AND placed_at >= NOW() - INTERVAL '7 days'
                  ), 0) AS week_revenue
                FROM orders.orders
                WHERE store_id = :storeId
                """;
        return jdbc.queryForObject(
                sql,
                new MapSqlParameterSource("storeId", storeId),
                (rs, n) -> new AnalyticsSummaryDto(
                        rs.getBigDecimal("today_revenue"),
                        rs.getInt("today_orders"),
                        rs.getInt("today_delivered"),
                        rs.getInt("pending_orders"),
                        rs.getBigDecimal("week_revenue"),
                        rs.getInt("week_orders")));
    }

    @Override
    public List<DailySummaryDto> getDailySummary(Long storeId, int days) {
        // Gross profit = revenue − COGS (cost_price is stored per order_item).
        // COGS is pre-aggregated per order in a CTE BEFORE joining to orders, so
        // each order contributes exactly one row: SUM(o.total) is the true revenue
        // (not multiplied by the order's line-item count) and SUM(oc.cogs) is the
        // matching cost. A plain JOIN to order_items here would fan out one row per
        // item and inflate revenue by the basket size.
        String sql = """
                WITH order_cogs AS (
                  SELECT order_id, SUM(qty * cost_price) AS cogs
                  FROM orders.order_items
                  GROUP BY order_id
                )
                SELECT
                  (o.placed_at AT TIME ZONE 'Asia/Kolkata')::date AS day,
                  COUNT(*)                                         AS orders,
                  COALESCE(SUM(o.total), 0)                        AS revenue,
                  COALESCE(SUM(oc.cogs), 0)                        AS cogs
                FROM orders.orders o
                LEFT JOIN order_cogs oc ON oc.order_id = o.id
                WHERE o.store_id = :storeId
                  AND o.status != 'CANCELLED'
                  AND o.placed_at >= NOW() - INTERVAL '1 day' * :days
                GROUP BY (o.placed_at AT TIME ZONE 'Asia/Kolkata')::date
                ORDER BY day DESC
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource("storeId", storeId).addValue("days", days),
                (rs, n) -> {
                    BigDecimal revenue = rs.getBigDecimal("revenue");
                    BigDecimal cogs = rs.getBigDecimal("cogs");
                    return new DailySummaryDto(
                            rs.getDate("day").toLocalDate().toString(),
                            revenue,
                            rs.getInt("orders"),
                            revenue.subtract(cogs));
                });
    }

    @Override
    public List<TopProductDto> getTopProducts(Long storeId, int days, int limit) {
        String sql = """
                SELECT
                  oi.product_name,
                  oi.label           AS variant_label,
                  SUM(oi.qty)        AS total_qty,
                  COALESCE(SUM(oi.line_total), 0) AS total_revenue
                FROM orders.order_items oi
                JOIN orders.orders o ON o.id = oi.order_id
                WHERE o.store_id = :storeId
                  AND o.status != 'CANCELLED'
                  AND o.placed_at >= NOW() - INTERVAL '1 day' * :days
                GROUP BY oi.product_name, oi.label
                ORDER BY total_qty DESC
                LIMIT :limit
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource("storeId", storeId)
                        .addValue("days", days)
                        .addValue("limit", limit),
                (rs, n) -> new TopProductDto(
                        rs.getString("product_name"),
                        rs.getString("variant_label"),
                        rs.getInt("total_qty"),
                        rs.getBigDecimal("total_revenue")));
    }

    @Override
    public List<LowStockDto> getLowStockItems(Long storeId) {
        String sql = """
                SELECT
                  sl.variant_id,
                  p.id           AS product_id,
                  p.name         AS product_name,
                  pv.label       AS variant_label,
                  (sl.on_hand - sl.reserved) AS available,
                  sl.low_stock_threshold
                FROM inventory.stock_levels sl
                JOIN catalog.product_variants pv ON pv.id = sl.variant_id
                JOIN catalog.products p          ON p.id  = pv.product_id
                WHERE sl.store_id = :storeId
                  AND (sl.on_hand - sl.reserved) <= sl.low_stock_threshold
                ORDER BY (sl.on_hand - sl.reserved) ASC
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource("storeId", storeId),
                (rs, n) -> new LowStockDto(
                        rs.getLong("variant_id"),
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getString("variant_label"),
                        rs.getInt("available"),
                        rs.getInt("low_stock_threshold")));
    }
}
