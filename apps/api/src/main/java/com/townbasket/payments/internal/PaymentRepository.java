package com.townbasket.payments.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for payments. */
interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
}
