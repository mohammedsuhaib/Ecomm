package com.townbasket.cart.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for carts. */
interface CartRepository extends JpaRepository<CartEntity, UUID> {

    /** The user's active (not-checked-out) cart, if any. */
    /**
     * The user's most recently touched open cart. Ordered on purpose: a user
     * accumulates several open carts over time (one per device/login path), and
     * an unordered findFirst lets Postgres pick an arbitrary — often stale —
     * one, so "my cart" could differ between logins.
     */
    Optional<CartEntity> findFirstByUserIdAndCheckedOutFalseOrderByUpdatedAtDesc(Long userId);
}
