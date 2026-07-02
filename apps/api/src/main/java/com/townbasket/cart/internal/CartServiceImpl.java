package com.townbasket.cart.internal;

import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartItemDto;
import com.townbasket.cart.CartNotFoundException;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.VariantView;
import com.townbasket.inventory.InventoryService;
import com.townbasket.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link CartService}. Persists cart lines as
 * {@code (variant_id, qty)} and resolves price/label/availability from the
 * {@code catalog} public API at read time (no cross-schema joins).
 */
@Service
@Transactional
class CartServiceImpl implements CartService {

    private final CartRepository carts;
    private final CatalogService catalog;
    private final InventoryService inventory;

    CartServiceImpl(CartRepository carts, CatalogService catalog, InventoryService inventory) {
        this.carts = carts;
        this.catalog = catalog;
        this.inventory = inventory;
    }

    @Override
    public CartDto createCart() {
        CartEntity cart = carts.save(new CartEntity(UUID.randomUUID()));
        return toDto(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartDto> getCart(UUID cartId) {
        return carts.findById(cartId).map(this::toDto);
    }

    @Override
    public CartDto addItem(UUID cartId, Long variantId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (variantId == null) {
            throw new IllegalArgumentException("variantId is required");
        }
        // Reject a variant the catalog doesn't know about (deleted/never existed):
        // otherwise it would sit in the cart resolving to ₹0 with a null name. An
        // existing-but-unavailable variant is still allowed in — it's flagged as
        // unavailable at read time and rejected at checkout, so the customer can
        // see and remove it rather than being blocked at "Add".
        if (catalog.findVariant(variantId).isEmpty()) {
            throw new BusinessRuleException("This item is no longer available.");
        }
        CartEntity cart = require(cartId);
        cart.getItems().stream()
                .filter(i -> i.getVariantId().equals(variantId))
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.setQty(existing.getQty() + qty),
                        () -> cart.getItems().add(new CartItemEntity(cart, variantId, qty)));
        cart.touch();
        return toDto(carts.saveAndFlush(cart));
    }

    @Override
    public CartDto updateItem(UUID cartId, Long itemId, int qty) {
        if (qty < 0) {
            throw new IllegalArgumentException("qty must not be negative");
        }
        CartEntity cart = require(cartId);
        CartItemEntity item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));
        if (qty == 0) {
            cart.getItems().remove(item);
        } else {
            item.setQty(qty);
        }
        cart.touch();
        return toDto(carts.saveAndFlush(cart));
    }

    @Override
    public CartDto removeItem(UUID cartId, Long itemId) {
        CartEntity cart = require(cartId);
        cart.getItems().removeIf(i -> i.getId().equals(itemId));
        cart.touch();
        return toDto(carts.saveAndFlush(cart));
    }

    @Override
    public void markCheckedOut(UUID cartId) {
        require(cartId).setCheckedOut(true);
    }

    @Override
    public CartDto createUserCart(Long userId) {
        CartEntity cart = carts.saveAndFlush(new CartEntity(UUID.randomUUID(), userId));
        return toDto(cart);
    }

    @Override
    public CartDto merge(UUID guestCartId, Long userId) {
        CartEntity guest = (guestCartId != null) ? carts.findById(guestCartId).orElse(null) : null;
        boolean guestUsable = guest != null && !guest.isCheckedOut() && !guest.getItems().isEmpty();

        Optional<CartEntity> activeUserCart = carts.findFirstByUserIdAndCheckedOutFalse(userId);

        // No active user cart: claim the guest cart (if usable), else return/create
        // the user's empty cart.
        if (activeUserCart.isEmpty()) {
            if (guestUsable) {
                guest.setUserId(userId);
                guest.touch();
                return toDto(carts.saveAndFlush(guest));
            }
            return createUserCart(userId);
        }

        // Active user cart exists: fold the guest lines in (sum qty per variant),
        // check the guest cart out, return the user cart.
        CartEntity userCart = activeUserCart.get();
        if (guestUsable && !guest.getId().equals(userCart.getId())) {
            for (CartItemEntity guestItem : guest.getItems()) {
                userCart.getItems().stream()
                        .filter(i -> i.getVariantId().equals(guestItem.getVariantId()))
                        .findFirst()
                        .ifPresentOrElse(
                                existing -> existing.setQty(existing.getQty() + guestItem.getQty()),
                                () -> userCart.getItems().add(
                                        new CartItemEntity(userCart, guestItem.getVariantId(), guestItem.getQty())));
            }
            guest.setCheckedOut(true);
            carts.save(guest);
        }
        userCart.touch();
        return toDto(carts.saveAndFlush(userCart));
    }

    private CartEntity require(UUID cartId) {
        return carts.findById(cartId).orElseThrow(() -> new CartNotFoundException(cartId));
    }

    private CartDto toDto(CartEntity cart) {
        List<CartItemDto> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int itemCount = 0;
        for (CartItemEntity item : cart.getItems()) {
            VariantView v = catalog.findVariant(item.getVariantId()).orElse(null);
            BigDecimal unitPrice = v != null ? v.unitPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQty()));
            boolean available = v != null && v.available() && unitPrice.signum() >= 0;
            int availableStock = available ? inventory.availability(item.getVariantId()) : 0;
            items.add(new CartItemDto(
                    item.getId(),
                    item.getVariantId(),
                    v != null ? v.productId() : null,
                    v != null ? v.productName() : null,
                    v != null ? v.label() : null,
                    unitPrice,
                    item.getQty(),
                    lineTotal,
                    available,
                    availableStock));
            subtotal = subtotal.add(lineTotal);
            itemCount += item.getQty();
        }
        return new CartDto(cart.getId(), items, subtotal, itemCount, cart.isCheckedOut());
    }
}
