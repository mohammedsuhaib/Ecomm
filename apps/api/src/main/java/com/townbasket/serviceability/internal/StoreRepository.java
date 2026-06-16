package com.townbasket.serviceability.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for stores. */
interface StoreRepository extends JpaRepository<StoreEntity, Long> {

    /** The single active store (MVP runs one store). */
    Optional<StoreEntity> findFirstByActiveTrueOrderByIdAsc();
}
