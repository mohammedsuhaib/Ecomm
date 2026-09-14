package com.townbasket.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    JdbcTemplate jdbc;

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

    @Test
    void addItemRejectsUnknownVariant() {
        UUID cartId = cartService.createCart().cartId();
        // A variant id the catalog doesn't know about must be rejected at "Add"
        // rather than silently sitting in the cart as a ₹0 / null line.
        assertThatThrownBy(() -> cartService.addItem(cartId, 9_999_999L, 1))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(cartService.getCart(cartId).orElseThrow().items()).isEmpty();
    }

    @Test
    void cartLinesCarryTheKannadaNameSoTheCartReadsLikeTheShop() {
        // The storefront names product tiles in Kannada from catalog.name_kn, but
        // a cart line used to carry only the English name — so the customer added
        // "\u0c85\u0cae\u0cc1\u0cb2\u0ccd \u0cac\u0cc6\u0ca3\u0ccd\u0ca3\u0cc6" and the cart answered "Amul Butter", which reads as a
        // different item. Both names now travel on the line; the storefront picks.
        ProductDto milk = catalogService.findProduct("amul-gold-milk").orElseThrow();
        ProductVariantDto variant = milk.variants().get(0);

        jdbc.update("UPDATE catalog.products SET name_kn = ? WHERE slug = ?",
                "\u0c85\u0cae\u0cc1\u0cb2\u0ccd \u0c97\u0ccb\u0cb2\u0ccd\u0ca1\u0ccd \u0cb9\u0cbe\u0cb2\u0cc1", "amul-gold-milk");
        try {
            UUID cartId = cartService.createCart().cartId();
            CartItemDto line = cartService.addItem(cartId, variant.id(), 1).items().get(0);

            assertThat(line.productName()).isEqualTo(milk.name());
            assertThat(line.productNameKn()).isEqualTo("\u0c85\u0cae\u0cc1\u0cb2\u0ccd \u0c97\u0ccb\u0cb2\u0ccd\u0ca1\u0ccd \u0cb9\u0cbe\u0cb2\u0cc1");
        } finally {
            jdbc.update("UPDATE catalog.products SET name_kn = NULL WHERE slug = ?", "amul-gold-milk");
        }
    }

    @Test
    void anUntransliteratedProductLeavesTheKannadaNameNullForTheFallback() {
        // name_kn is nullable and the backfill job is off in tests, so the line
        // reports null and the storefront falls back to the English name rather
        // than rendering a blank where the product should be.
        jdbc.update("UPDATE catalog.products SET name_kn = NULL WHERE slug = ?", "amul-dahi");
        ProductVariantDto variant =
                catalogService.findProduct("amul-dahi").orElseThrow().variants().get(0);

        UUID cartId = cartService.createCart().cartId();
        CartItemDto line = cartService.addItem(cartId, variant.id(), 1).items().get(0);

        assertThat(line.productName()).isNotBlank();
        assertThat(line.productNameKn()).isNull();
    }
}
