package com.townbasket.cart;

import java.util.Optional;
import java.util.UUID;

/**
 * Published API of the cart module. Anonymous carts (M3) keyed by a
 * server-generated UUID. Line price/availability are resolved from the catalog
 * public API at read time (no cross-schema joins).
 *
 * <p>The {@code orders} module reads a cart (to build the order) and marks it
 * checked out via {@link #markCheckedOut(UUID)} — both by id only.
 */
public interface CartService {

    /** Create a new empty cart and return it. */
    CartDto createCart();

    /** Fetch a cart by id, with lines resolved against the catalog. */
    Optional<CartDto> getCart(UUID cartId);

    /** Add a variant (or increase its qty) and return the updated cart. */
    CartDto addItem(UUID cartId, Long variantId, int qty);

    /** Set a line's quantity (0 removes the line) and return the updated cart. */
    CartDto updateItem(UUID cartId, Long itemId, int qty);

    /** Remove a line and return the updated cart. */
    CartDto removeItem(UUID cartId, Long itemId);

    /** Mark a cart as checked out (called by the orders checkout). No-op if already checked out. */
    void markCheckedOut(UUID cartId);
}
