package com.townbasket.orders.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for orders. */
interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderEntity> findByPublicToken(UUID publicToken);

    Page<OrderEntity> findAllByOrderByPlacedAtDescIdDesc(Pageable pageable);

    Page<OrderEntity> findByStatusOrderByPlacedAtDescIdDesc(OrderStatus status, Pageable pageable);

    Page<OrderEntity> findByUserIdOrderByPlacedAtDescIdDesc(Long userId, Pageable pageable);

    Page<OrderEntity> findByAssignedAgentIdOrderByPlacedAtDescIdDesc(Long agentId, Pageable pageable);

    Page<OrderEntity> findByAssignedAgentIdAndStatusOrderByPlacedAtDescIdDesc(
            Long agentId, OrderStatus status, Pageable pageable);
}
