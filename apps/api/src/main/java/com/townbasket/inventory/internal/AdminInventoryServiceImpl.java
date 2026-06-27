package com.townbasket.inventory.internal;

import com.townbasket.inventory.AdminInventoryService;
import com.townbasket.inventory.StockLevelDto;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.PagedResponse;
import com.townbasket.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin inventory operations backed by native SQL for the list (joins catalog
 * tables for product/variant names) and JPA for corrections (validated write).
 */
@Service
@Transactional
class AdminInventoryServiceImpl implements AdminInventoryService {

    private final StockLevelRepository stockLevels;
    private final StockMovementRepository movements;
    private final NamedParameterJdbcTemplate jdbc;

    AdminInventoryServiceImpl(StockLevelRepository stockLevels,
                              StockMovementRepository movements,
                              NamedParameterJdbcTemplate jdbc) {
        this.stockLevels = stockLevels;
        this.movements = movements;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockLevelDto> listStockLevels(Long storeId, int page, int size) {
        String countSql = """
                SELECT COUNT(*) FROM inventory.stock_levels WHERE store_id = :storeId
                """;
        Long total = jdbc.queryForObject(countSql,
                new MapSqlParameterSource("storeId", storeId), Long.class);

        String listSql = """
                SELECT
                  sl.id,
                  sl.variant_id,
                  p.id           AS product_id,
                  p.name         AS product_name,
                  pv.label       AS variant_label,
                  pv.selling_price,
                  sl.on_hand,
                  sl.reserved,
                  (sl.on_hand - sl.reserved) AS available,
                  sl.low_stock_threshold
                FROM inventory.stock_levels sl
                JOIN catalog.product_variants pv ON pv.id = sl.variant_id
                JOIN catalog.products p          ON p.id  = pv.product_id
                WHERE sl.store_id = :storeId
                ORDER BY p.name, pv.label
                LIMIT :size OFFSET :offset
                """;
        List<StockLevelDto> content = jdbc.query(
                listSql,
                new MapSqlParameterSource("storeId", storeId)
                        .addValue("size", size)
                        .addValue("offset", (long) page * size),
                (rs, n) -> new StockLevelDto(
                        rs.getLong("id"),
                        rs.getLong("variant_id"),
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getString("variant_label"),
                        rs.getBigDecimal("selling_price"),
                        rs.getInt("on_hand"),
                        rs.getInt("reserved"),
                        rs.getInt("available"),
                        rs.getInt("low_stock_threshold")));

        return new PagedResponse<>(content, page, size, total == null ? 0L : total);
    }

    @Override
    public void correctStock(Long storeId, Long variantId, int newOnHand, String reason) {
        if (newOnHand < 0) {
            throw new BusinessRuleException("newOnHand must be >= 0, got " + newOnHand);
        }
        StockLevelEntity entity = stockLevels.findByStoreIdAndVariantId(storeId, variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No stock level for variant " + variantId + " at store " + storeId));

        int delta = newOnHand - entity.getOnHand();
        if (delta == 0) return;

        stockLevels.setOnHand(storeId, variantId, newOnHand);
        String movReason = (reason != null && !reason.isBlank() ? reason : "physical count") + " [correction Δ" + delta + "]";
        movements.save(new StockMovementEntity(variantId, delta, movReason));
    }
}
