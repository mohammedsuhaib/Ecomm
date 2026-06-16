package com.townbasket.inventory.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for the stock-movement audit ledger. */
interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {
}
