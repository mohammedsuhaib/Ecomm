package com.townbasket.inventory.internal;

import com.townbasket.inventory.InsufficientStockException;
import com.townbasket.inventory.InventoryService;
import com.townbasket.inventory.ReservationLine;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link InventoryService}.
 *
 * <p>Reservation is an atomic conditional UPDATE per line; if any line cannot be
 * satisfied the whole method throws and the surrounding transaction rolls back,
 * so the orders checkout never sees a partial reservation. Commit/release are
 * driven by order events (see {@link InventoryOrderEventListener}).
 */
@Service
@Transactional
class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final StockLevelRepository stockLevels;
    private final ReservationRepository reservations;
    private final StockMovementRepository movements;

    InventoryServiceImpl(StockLevelRepository stockLevels,
                         ReservationRepository reservations,
                         StockMovementRepository movements) {
        this.stockLevels = stockLevels;
        this.reservations = reservations;
        this.movements = movements;
    }

    @Override
    public void reserve(Long storeId, Long orderId, List<ReservationLine> lines) {
        for (ReservationLine line : lines) {
            int updated = stockLevels.reserve(storeId, line.variantId(), line.qty());
            if (updated == 0) {
                int available = stockLevels.findByStoreIdAndVariantId(storeId, line.variantId())
                        .map(StockLevelEntity::available)
                        .orElse(0);
                // Triggers rollback of any reservations already applied in this tx.
                // A lost sale, and the one inventory event worth reading back:
                // it means the shelf count and the catalogue had drifted apart
                // by the time someone tried to buy.
                log.warn("Reservation refused for order {}: variant {} wanted {}, available {}",
                        orderId, line.variantId(), line.qty(), available);
                throw new InsufficientStockException(line.variantId(), line.qty(), available);
            }
            reservations.save(new ReservationEntity(orderId, line.variantId(), line.qty()));
            movements.save(new StockMovementEntity(line.variantId(), -line.qty(), "RESERVE order " + orderId));
        }
    }

    @Override
    public void commitReservation(Long orderId) {
        List<ReservationEntity> active = reservations.findByOrderIdAndStatus(orderId, ReservationEntity.RESERVED);
        for (ReservationEntity r : active) {
            stockLevels.commit(r.getVariantId(), r.getQty());
            r.setStatus(ReservationEntity.COMMITTED);
            movements.save(new StockMovementEntity(r.getVariantId(), -r.getQty(), "COMMIT order " + orderId));
        }
    }

    @Override
    public void releaseReservation(Long orderId) {
        List<ReservationEntity> active = reservations.findByOrderIdAndStatus(orderId, ReservationEntity.RESERVED);
        for (ReservationEntity r : active) {
            stockLevels.release(r.getVariantId(), r.getQty());
            r.setStatus(ReservationEntity.RELEASED);
            movements.save(new StockMovementEntity(r.getVariantId(), r.getQty(), "RELEASE order " + orderId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int availability(Long variantId) {
        return stockLevels.findFirstByVariantIdOrderByIdAsc(variantId)
                .map(StockLevelEntity::available)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> availability(Collection<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (StockLevelEntity s : stockLevels.findByVariantIdIn(variantIds)) {
            // Single-store MVP keeps one row per variant; if a variant ever has
            // rows at multiple stores, surface the largest available count.
            result.merge(s.getVariantId(), s.available(), Math::max);
        }
        return result;
    }
}
