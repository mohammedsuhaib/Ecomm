package com.townbasket.inventory.internal;

import com.townbasket.inventory.AdminInventoryService;
import com.townbasket.inventory.StockLevelDto;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.PagedResponse;
import com.townbasket.shared.ResourceNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AdminInventoryServiceImpl.class);

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
    public PagedResponse<StockLevelDto> listStockLevels(Long storeId, String q, int page, int size) {
        String term = q == null || q.isBlank() ? null : q.trim();
        MapSqlParameterSource params = new MapSqlParameterSource("storeId", storeId);
        // The search has to reach the catalog join, so even the COUNT needs it —
        // otherwise the pager would describe the unfiltered set.
        String searchJoin = "";
        String searchWhere = "";
        if (term != null) {
            searchJoin = """
                    JOIN catalog.product_variants pv ON pv.id = sl.variant_id
                    JOIN catalog.products p          ON p.id  = pv.product_id
                    """;
            searchWhere = " AND (p.name ILIKE :like OR pv.label ILIKE :like)";
            params.addValue("like", "%" + escapeLike(term) + "%");
        }

        String countSql = "SELECT COUNT(*) FROM inventory.stock_levels sl "
                + searchJoin + " WHERE sl.store_id = :storeId" + searchWhere;
        Long total = jdbc.queryForObject(countSql, params, Long.class);

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
                %SEARCH%
                -- Surface stock that needs attention first: out-of-stock, then
                -- low-stock (at/below threshold), then healthy. Within each bucket
                -- the scarcest items lead; product/variant name breaks ties.
                ORDER BY
                  CASE
                    WHEN (sl.on_hand - sl.reserved) <= 0 THEN 0
                    WHEN (sl.on_hand - sl.reserved) <= sl.low_stock_threshold THEN 1
                    ELSE 2
                  END,
                  (sl.on_hand - sl.reserved) ASC,
                  p.name, pv.label
                LIMIT :size OFFSET :offset
                """;
        List<StockLevelDto> content = jdbc.query(
                listSql.replace("%SEARCH%", searchWhere),
                params.addValue("size", size)
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

    /**
     * Neutralise LIKE wildcards in user input so searching "50%" or "a_b" is a
     * literal match rather than a match-everything pattern.
     */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public void correctStock(Long storeId, Long variantId, int newOnHand, String reason) {
        // These messages are shown verbatim to store staff in the admin UI, so
        // they read as sentences and never leak a request field name.
        if (newOnHand < 0) {
            throw new BusinessRuleException("The count can't be negative (got " + newOnHand + ").");
        }
        StockLevelEntity entity = stockLevels.findByStoreIdAndVariantId(storeId, variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No stock level for variant " + variantId + " at store " + storeId));

        // A physical count must not drop on_hand below units already reserved for
        // open orders, or available (on_hand - reserved) goes negative and commit()
        // would later drive on_hand negative too. Reserved stock is physically
        // committed to existing orders; release those first if the count is truly lower.
        if (newOnHand < entity.getReserved()) {
            throw new BusinessRuleException(
                    "The count can't be set to " + newOnHand + ": " + entity.getReserved()
                            + " unit(s) are already reserved for open orders. "
                            + "Cancel or fulfil those orders before lowering the count this far.");
        }

        int previousOnHand = entity.getOnHand();
        int delta = newOnHand - previousOnHand;
        if (delta == 0) return;

        stockLevels.setOnHand(storeId, variantId, newOnHand);
        String movReason = (reason != null && !reason.isBlank() ? reason : "physical count") + " [correction Δ" + delta + "]";
        movements.save(new StockMovementEntity(variantId, delta, movReason));
        // A human overriding the system's count. The movement row is the
        // permanent record; this is so an unexplained stock swing can be found
        // in the log next to whatever else happened at that moment.
        log.info("Stock corrected at store {}: variant {} {} -> {} (Δ{}), reason: {}",
                storeId, variantId, previousOnHand, newOnHand, delta, movReason);
    }
}
