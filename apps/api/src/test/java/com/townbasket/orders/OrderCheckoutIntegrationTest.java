package com.townbasket.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.inventory.InventoryService;
import com.townbasket.payments.PaymentMethod;
import com.townbasket.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * End-to-end checkout + state-machine integration test against a real Postgres
 * (Testcontainers). Covers: COD and UPI(fake) checkout reserving stock and
 * confirming, the admin transition flow incl. DELIVERED requiring the OTP,
 * cancellation releasing stock, and idempotent checkout.
 */
class OrderCheckoutIntegrationTest extends AbstractIntegrationTest {

    // Seeded store coordinates — within the 5 km radius (serviceable).
    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;

    @Autowired
    OrderService orderService;
    @Autowired
    CartService cartService;
    @Autowired
    CatalogService catalogService;
    @Autowired
    InventoryService inventoryService;

    /** A variant whose selling price * qty clears the ₹299 minimum. */
    private ProductVariantDto pickPricyVariant() {
        for (ProductDto p : catalogService.listProducts(null, false, null, PageRequest.of(0, 100)).content()) {
            for (ProductVariantDto v : p.variants()) {
                if (v.available() && v.sellingPrice().compareTo(BigDecimal.valueOf(120)) >= 0) {
                    return v;
                }
            }
        }
        throw new IllegalStateException("No suitable seeded variant found");
    }

    private CartDto cartWithValue(ProductVariantDto variant, int qty) {
        UUID cartId = cartService.createCart().cartId();
        return cartService.addItem(cartId, variant.id(), qty);
    }

    private PlaceOrderRequest request(UUID cartId, PaymentMethod method) {
        return new PlaceOrderRequest(
                cartId, "Asha Rao", "9999900000",
                new AddressDto("12 MG Road", STORE_LAT, STORE_LNG),
                method, null);
    }

    @Test
    void codCheckoutReservesStockConfirmsAndExposesOtpButNotCostPrice() {
        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());

        CartDto cart = cartWithValue(variant, 5);
        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "cod-key-1", null);

        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(order.paymentMethod()).isEqualTo("COD");
        assertThat(order.paymentStatus()).isEqualTo("COD_PENDING");
        assertThat(order.deliveryOtp()).hasSize(6);
        assertThat(order.items()).allSatisfy(i -> assertThat(i.unitPrice()).isNotNull());
        // OrderItemDto has no cost-price accessor at all (compile-time guarantee).
        assertThat(order.timeline()).extracting(OrderTimelineEntryDto::toStatus)
                .containsExactly("PLACED", "CONFIRMED");

        // Stock reserved -> availability dropped by 5.
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - 5);
    }

    @Test
    void upiFakeCheckoutConfirmsAndMarksPaid() {
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, 5);

        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.UPI), "upi-key-1", null);

        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(order.paymentMethod()).isEqualTo("UPI");
        assertThat(order.paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void checkoutIsIdempotentOnKey() {
        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());
        CartDto cart = cartWithValue(variant, 5);

        OrderDto first = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "idem-key-1", null);
        OrderDto second = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "idem-key-1", null);

        assertThat(second.id()).isEqualTo(first.id());
        // Stock reserved only once.
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - 5);
    }

    @Test
    void belowMinimumOrderValueIsRejected() {
        // Cheapest single unit (≤ ₹40) is well below the ₹299 minimum.
        ProductVariantDto cheap = catalogService.listProducts(null, false, null, PageRequest.of(0, 100)).content().stream()
                .flatMap(p -> p.variants().stream())
                .filter(v -> v.sellingPrice().compareTo(BigDecimal.valueOf(40)) <= 0)
                .findFirst().orElseThrow();
        CartDto cart = cartWithValue(cheap, 1);

        assertThatThrownBy(() -> orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "min-key-1", null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void outOfRadiusAddressIsRejected() {
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, 5);
        PlaceOrderRequest req = new PlaceOrderRequest(
                cart.cartId(), "Asha Rao", "9999900000",
                new AddressDto("Far away", 12.2958, 76.6394), // ~25 km
                PaymentMethod.COD, null);

        assertThatThrownBy(() -> orderService.placeOrder(req, "radius-key-1", null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adminTransitionFlowDeliveredRequiresMatchingOtp() {
        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());
        CartDto cart = cartWithValue(variant, 5);
        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "flow-key-1", null);

        Long id = order.id();
        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, null));

        // Wrong OTP rejected.
        assertThatThrownBy(() -> orderService.transition(id, new TransitionRequest("DELIVERED", "000000", null)))
                .isInstanceOf(BusinessRuleException.class);

        // Correct OTP delivers; COD becomes PAID.
        OrderDto delivered = orderService.transition(id, new TransitionRequest("DELIVERED", order.deliveryOtp(), null));
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.paymentStatus()).isEqualTo("PAID");

        // OrderDelivered is consumed by inventory (async, after commit) to COMMIT
        // the reservation: on_hand drops, so available stays reduced by 5.
        eventually(() -> assertThat(inventoryService.availability(variant.id())).isEqualTo(before - 5));
    }

    @Test
    void illegalTransitionIsRejected() {
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, 5);
        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "illegal-key-1", null);

        // CONFIRMED cannot jump straight to DELIVERED.
        assertThatThrownBy(() -> orderService.transition(order.id(),
                new TransitionRequest("DELIVERED", order.deliveryOtp(), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancellationReleasesStock() {
        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());
        CartDto cart = cartWithValue(variant, 5);

        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "cancel-key-1", null);
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - 5);

        orderService.transition(order.id(), new TransitionRequest("CANCELLED", null, "Customer changed mind"));

        // OrderCancelled is consumed by inventory (async, after commit) to RELEASE
        // the reservation -> availability restored.
        eventually(() -> assertThat(inventoryService.availability(variant.id())).isEqualTo(before));
    }

    /**
     * Poll an assertion until it passes or a short timeout elapses. The
     * inventory commit/release runs in an {@code @ApplicationModuleListener}
     * (after-commit, in its own transaction), so the effect is not visible
     * synchronously when the transition call returns.
     */
    private static void eventually(Runnable assertion) {
        AssertionError last = null;
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                last = e;
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
            }
        }
        if (last != null) {
            throw last;
        }
    }

    @Test
    void adminListReturnsNewestFirst() {
        ProductVariantDto variant = pickPricyVariant();
        OrderDto o1 = orderService.placeOrder(
                request(cartWithValue(variant, 5).cartId(), PaymentMethod.COD), "list-key-1", null);
        OrderDto o2 = orderService.placeOrder(
                request(cartWithValue(variant, 5).cartId(), PaymentMethod.COD), "list-key-2", null);

        List<OrderDto> all = orderService.listOrders(null, PageRequest.of(0, 50)).content();
        List<Long> ids = all.stream().map(OrderDto::id).toList();
        assertThat(ids).contains(o1.id(), o2.id());
        // Newest (o2) appears before older (o1).
        assertThat(ids.indexOf(o2.id())).isLessThan(ids.indexOf(o1.id()));
    }
}
