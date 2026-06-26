package com.townbasket.cart.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for carts. */
interface CartRepository extends JpaRepository<CartEntity, UUID> {

    /** The user's active (not-checked-out) cart, if any. */
    Optional<CartEntity> findFirstByUserIdAndCheckedOutFalse(Long userId);
}
