package com.townbasket.inventory.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for reservations. */
interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    List<ReservationEntity> findByOrderIdAndStatus(Long orderId, String status);
}
