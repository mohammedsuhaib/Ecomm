package com.townbasket.orders.internal;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for orders. */
interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    Page<OrderEntity> findAllByOrderByPlacedAtDescIdDesc(Pageable pageable);

    Page<OrderEntity> findByStatusOrderByPlacedAtDescIdDesc(OrderStatus status, Pageable pageable);
}
