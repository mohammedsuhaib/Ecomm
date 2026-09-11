package com.townbasket.cart;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous-cart REST API under {@code /api/v1}. Part of the cart module's
 * published surface; returns {@link CartDto} only.
 */
@RestController
@RequestMapping("/api/v1/carts")
@Tag(name = "Cart", description = "Anonymous server-side cart (keyed by a server-generated cart id).")
class CartController {

    private final CartService cartService;

    CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * The principal is OPTIONAL here (the route is public): the JWT filter runs
     * on every request, so a logged-in caller's cart is owned from birth and can
     * be found from another device via {@code GET /carts/mine}; a guest's cart
     * stays anonymous until the login merge claims it.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new empty cart; returns the cart (with its generated id).")
    CartDto createCart(@AuthenticationPrincipal Long userId) {
        return cartService.createCart(userId);
    }

    /**
     * How a device with no local cartId finds the basket after login. 404 (not
     * an empty cart) when the user has no open cart, so the client keeps its
     * lazy create-on-first-add behaviour.
     */
    @GetMapping("/mine")
    @Operation(summary = "The caller's most recently used open cart (AUTHENTICATED).")
    ResponseEntity<CartDto> myCart(@AuthenticationPrincipal Long userId) {
        return cartService.activeCartFor(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{cartId}")
    @Operation(summary = "Get a cart by id, with lines priced/validated against the catalog.")
    ResponseEntity<CartDto> getCart(@PathVariable UUID cartId) {
        return cartService.getCart(cartId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{cartId}/items")
    @Operation(summary = "Add a variant to the cart (or increase its quantity).")
    CartDto addItem(@PathVariable UUID cartId, @RequestBody AddItemRequest request) {
        return cartService.addItem(cartId, request.variantId(), request.qty());
    }

    @PutMapping("/{cartId}/items/{itemId}")
    @Operation(summary = "Set a line's quantity (0 removes the line).")
    CartDto updateItem(@PathVariable UUID cartId, @PathVariable Long itemId, @RequestBody UpdateItemRequest request) {
        return cartService.updateItem(cartId, itemId, request.qty());
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    @Operation(summary = "Remove a line from the cart.")
    CartDto removeItem(@PathVariable UUID cartId, @PathVariable Long itemId) {
        return cartService.removeItem(cartId, itemId);
    }

    /**
     * The caller's user id is the security principal (a plain {@code Long} set
     * by the JWT filter) — the cart module deliberately does not depend on the
     * identity module for this. The security config guarantees the merge route
     * is only reached with a valid token.
     */
    @PostMapping("/{cartId}/merge")
    @Operation(summary = "Merge a guest cart into the caller's active cart (AUTHENTICATED).")
    CartDto merge(@PathVariable UUID cartId, @AuthenticationPrincipal Long userId) {
        return cartService.merge(cartId, userId);
    }

    /** Body for adding a cart item. */
    record AddItemRequest(Long variantId, int qty) {
    }

    /** Body for updating a cart item's quantity. */
    record UpdateItemRequest(int qty) {
    }
}
