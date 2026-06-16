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

    /**
     * Create a new empty cart owned by {@code userId} and return it. Used on
     * login (no guest cart to merge) and by {@code orders} reorder.
     */
    CartDto createUserCart(Long userId);

    /**
     * Merge the guest cart {@code guestCartId} into {@code userId}'s active cart
     * (M4_CONTRACT §5):
     * <ul>
     *   <li>no active user cart → claim the guest cart for the user;</li>
     *   <li>active user cart exists → add the guest lines into it (sum qty per
     *       variant), check the guest cart out, return the USER cart;</li>
     *   <li>guest cart missing/empty → return the user's active cart (creating an
     *       empty one if none). Never fails on a stale guest id.</li>
     * </ul>
     * The returned {@code cartId} may differ from {@code guestCartId} — the
     * client must store it.
     */
    CartDto merge(UUID guestCartId, Long userId);
}
