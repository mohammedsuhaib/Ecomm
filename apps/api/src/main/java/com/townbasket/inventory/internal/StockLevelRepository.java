package com.townbasket.inventory.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Module-internal Spring Data repository for stock levels. */
interface StockLevelRepository extends JpaRepository<StockLevelEntity, Long> {

    Optional<StockLevelEntity> findByStoreIdAndVariantId(Long storeId, Long variantId);

    Optional<StockLevelEntity> findFirstByVariantIdOrderByIdAsc(Long variantId);

    /**
     * Atomically reserve {@code qty} units: the single conditional UPDATE only
     * succeeds when enough stock is available ({@code on_hand - reserved >= qty}),
     * making oversell impossible under concurrency. Returns rows updated (1 = ok).
     */
    @Modifying
    @Query("""
            UPDATE StockLevelEntity s
               SET s.reserved = s.reserved + :qty
             WHERE s.storeId = :storeId
               AND s.variantId = :variantId
               AND s.onHand - s.reserved >= :qty
            """)
    int reserve(@Param("storeId") Long storeId,
                @Param("variantId") Long variantId,
                @Param("qty") int qty);

    /** Commit a reservation on delivery: on_hand -= qty, reserved -= qty. */
    @Modifying
    @Query("""
            UPDATE StockLevelEntity s
               SET s.onHand = s.onHand - :qty,
                   s.reserved = s.reserved - :qty
             WHERE s.variantId = :variantId
            """)
    int commit(@Param("variantId") Long variantId, @Param("qty") int qty);

    /** Release a reservation on cancellation: reserved -= qty. */
    @Modifying
    @Query("""
            UPDATE StockLevelEntity s
               SET s.reserved = s.reserved - :qty
             WHERE s.variantId = :variantId
            """)
    int release(@Param("variantId") Long variantId, @Param("qty") int qty);
}
