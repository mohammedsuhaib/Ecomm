package com.townbasket.cart.internal;

import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartItemDto;
import com.townbasket.cart.CartNotFoundException;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.VariantView;
import com.townbasket.inventory.InventoryService;
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
