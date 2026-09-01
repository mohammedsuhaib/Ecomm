package com.townbasket.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.orders.AddressDto;
import com.townbasket.orders.OrderService;
import com.townbasket.orders.PlaceOrderRequest;
import com.townbasket.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

/**
 * Behaviour with online payment switched OFF — the production default while the
 * only UPI provider is a fake that auto-succeeds.
 *
 * <p>Overrides the test-wide {@code upi-enabled: true} (which exists so the UPI
 * plumbing stays covered) to assert what a real deployment does: UPI is not
 * offered, and choosing it anyway is refused rather than quietly marking an
 * order PAID with no money received.
 */
@TestPropertySource(properties = "townbasket.payments.upi-enabled=false")
class PaymentMethodsIntegrationTest extends AbstractIntegrationTest {

    // The seeded store sits here; ordering needs a serviceable address.
    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;

    /** Units per line — 5 x >= Rs.120 clears the store's minimum order value. */
    private static final int QTY = 5;

    @Autowired
    PaymentService paymentService;

    @Autowired
    OrderService orderService;

    @Autowired
    CartService cartService;

    @Autowired
    CatalogService catalogService;

    @Test
    void onlyCashOnDeliveryIsOffered() {
        assertThat(paymentService.enabledMethods()).containsExactly(PaymentMethod.COD);
    }

    @Test
    void choosingUpiIsRefused() {
        assertThatThrownBy(() -> paymentService.requireEnabled(PaymentMethod.UPI))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cash on Delivery");

        // A null method is not a silent pass either.
        assertThatThrownBy(() -> paymentService.requireEnabled(null))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> paymentService.charge(1L, PaymentMethod.UPI, BigDecimal.TEN))
                .as("even a direct charge cannot bypass the switch")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void checkoutRefusesUpiAndLeavesTheCartUsableForCash() {
        CartDto cart = cartWorthOrdering();

        assertThatThrownBy(() -> orderService.placeOrder(
                request(cart.cartId(), PaymentMethod.UPI), "upi-off-" + UUID.randomUUID(), null))
                .isInstanceOf(BusinessRuleException.class);

        // The rejection must not consume the cart — the customer switches to COD
        // and orders the same basket.
        assertThat(cartService.getCart(cart.cartId()).orElseThrow().checkedOut()).isFalse();
        assertThat(orderService.placeOrder(
                request(cart.cartId(), PaymentMethod.COD), "cod-after-upi-" + UUID.randomUUID(), null)
                .paymentMethod()).isEqualTo("COD");
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * A cart whose subtotal clears the store's minimum order value.
     *
     * <p>Stock matters as much as price here. The Testcontainers Postgres is a
     * singleton shared by every integration test, and variants created by other
     * tests (admin "add variant", CSV import) open their stock row at zero — so
     * picking on price alone can land on a listed-but-unbuyable variant and fail
     * with InsufficientStock. Require live sellable stock for the whole quantity.
     */
    private CartDto cartWorthOrdering() {
        for (ProductDto p : catalogService.listProducts(null, false, null, PageRequest.of(0, 200)).content()) {
            for (ProductVariantDto v : p.variants()) {
                if (v.available()
                        && v.availableStock() >= QTY
                        && v.sellingPrice().compareTo(BigDecimal.valueOf(120)) >= 0) {
                    UUID cartId = cartService.createCart().cartId();
                    return cartService.addItem(cartId, v.id(), QTY);
                }
            }
        }
        throw new IllegalStateException("No seeded variant with price >= 120 and " + QTY + " units in stock");
    }

    private static PlaceOrderRequest request(UUID cartId, PaymentMethod method) {
        return new PlaceOrderRequest(
                cartId, "Asha Rao", "9999900000",
                new AddressDto("12 MG Road", STORE_LAT, STORE_LNG),
                method, null, null);
    }
}
