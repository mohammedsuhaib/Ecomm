package com.townbasket.cart;

import com.townbasket.shared.ResourceNotFoundException;
import java.util.UUID;

/**
 * Thrown when a cart id does not resolve to an existing cart. Mapped to 404 by
 * the global handler.
 */
public class CartNotFoundException extends ResourceNotFoundException {

    public CartNotFoundException(UUID cartId) {
        super("Cart not found: " + cartId);
    }
}
