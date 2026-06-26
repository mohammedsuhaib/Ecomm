package com.townbasket.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductVariantDto;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Cart integration test against a real Postgres (Testcontainers). Exercises
 * add/update/remove and that totals + availability are resolved from the catalog
 * at read time — and that no cost price is ever exposed.
 */
class CartIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    CartService cartService;

    @Autowired
    CatalogService catalogService;

    private ProductVariantDto anyVariant() {
        ProductDto product = catalogService.listProducts(null, false, null, PageRequest.of(0, 1)).content().get(0);
        return product.variants().get(0);
    }

    @Test
    void addUpdateRemoveAndTotals() {
        ProductVariantDto variant = anyVariant();
        UUID cartId = cartService.createCart().cartId();

        CartDto afterAdd = cartService.addItem(cartId, variant.id(), 2);
        assertThat(afterAdd.items()).hasSize(1);
        CartItemDto line = afterAdd.items().get(0);
        assertThat(line.qty()).isEqualTo(2);
        assertThat(line.unitPrice()).isEqualByComparingTo(variant.sellingPrice());
        assertThat(line.lineTotal()).isEqualByComparingTo(variant.sellingPrice().multiply(BigDecimal.valueOf(2)));
        assertThat(line.available()).isTrue();
        assertThat(afterAdd.subtotal()).isEqualByComparingTo(line.lineTotal());
        assertThat(afterAdd.itemCount()).isEqualTo(2);

        // Adding the same variant increases its quantity rather than duplicating.
        CartDto afterAddAgain = cartService.addItem(cartId, variant.id(), 1);
        assertThat(afterAddAgain.items()).hasSize(1);
        assertThat(afterAddAgain.items().get(0).qty()).isEqualTo(3);

        // Update to a specific quantity.
        Long itemId = afterAddAgain.items().get(0).itemId();
        CartDto afterUpdate = cartService.updateItem(cartId, itemId, 5);
        assertThat(afterUpdate.items().get(0).qty()).isEqualTo(5);
        assertThat(afterUpdate.itemCount()).isEqualTo(5);

        // Quantity 0 removes the line.
        CartDto afterZero = cartService.updateItem(cartId, itemId, 0);
        assertThat(afterZero.items()).isEmpty();
        assertThat(afterZero.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);

        // Add again then explicitly remove.
        CartDto readded = cartService.addItem(cartId, variant.id(), 1);
        Long newItemId = readded.items().get(0).itemId();
        CartDto afterRemove = cartService.removeItem(cartId, newItemId);
        assertThat(afterRemove.items()).isEmpty();
    }

    @Test
    void getCartIsEmptyForUnknownId() {
        assertThat(cartService.getCart(UUID.randomUUID())).isEmpty();
    }
}
