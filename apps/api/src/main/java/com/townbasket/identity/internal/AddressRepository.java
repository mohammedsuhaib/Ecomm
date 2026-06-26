package com.townbasket.identity.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for saved addresses. */
interface AddressRepository extends JpaRepository<AddressEntity, Long> {

    /** Default first (is_default DESC), then newest (id DESC). */
    List<AddressEntity> findByUserIdOrderByIsDefaultDescIdDesc(Long userId);

    Optional<AddressEntity> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
