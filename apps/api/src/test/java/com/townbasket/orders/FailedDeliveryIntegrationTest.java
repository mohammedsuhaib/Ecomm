package com.townbasket.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.identity.AuthService;
import com.townbasket.identity.CreateDeliveryAgentRequest;
import com.townbasket.identity.PhoneVerifyRequest;
import com.townbasket.inventory.InventoryService;
import com.townbasket.payments.PaymentMethod;
import com.townbasket.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

/**
 * The failed-delivery branch of the state machine against a real Postgres:
 * a rider who cannot complete a delivery parks the order in DELIVERY_FAILED
 * WITHOUT releasing stock (the goods are still in their bag), and staff then
 * either re-dispatch it or cancel it — only the cancel puts stock back.
 */
class FailedDeliveryIntegrationTest extends AbstractIntegrationTest {

    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;
    private static final int QTY = 5;

    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired CatalogService catalogService;
    @Autowired InventoryService inventoryService;
    @Autowired AuthService authService;

    @Test
    void riderFailsDelivery_stockStaysReserved_untilStaffCancel() {
        ProductVariantDto variant = buyableVariant();
        int before = inventoryService.availability(variant.id());
        Long agentId = newAgent("fail-rider-1");
        Long id = dispatch(variant, agentId, "fail-key-1");
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - QTY);

        OrderDto failed = orderService.failDelivery(id, agentId, "Customer not reachable");
        assertThat(failed.status()).isEqualTo("DELIVERY_FAILED");
        // The reason is on the timeline for staff and the customer to read.
        assertThat(failed.timeline()).last()
                .satisfies(e -> {
                    assertThat(e.toStatus()).isEqualTo("DELIVERY_FAILED");
                    assertThat(e.note()).isEqualTo("Customer not reachable");
                });

        // The goods are on the bike, not the shelf: NOTHING is released yet.
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - QTY);

        // Staff give up and cancel once the bag is back — now stock returns.
        orderService.transition(id, new TransitionRequest("CANCELLED", null, "Returned to store"));
        eventually(() -> assertThat(inventoryService.availability(variant.id())).isEqualTo(before));
    }

    @Test
    void failedDeliveryCanBeRedispatchedAndThenDelivered() {
        ProductVariantDto variant = buyableVariant();
        Long agentId = newAgent("fail-rider-2");
        // Token reads are owner-scoped, so this order needs a real customer.
        Long customerId = authService.phoneVerify(new PhoneVerifyRequest("dev:9990005555")).user().id();
        Long id = orderService.placeOrder(request(cart(variant).cartId()), "fail-key-2", customerId).id();
        orderService.assignAgent(id, agentId);
        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, null));
        orderService.failDelivery(id, agentId, "Customer asked to deliver later");

        OrderDto again = orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, "Second attempt"));
        assertThat(again.status()).isEqualTo("OUT_FOR_DELIVERY");
        // The same rider still holds it and the OTP is unchanged, so the customer's code still works.
        assertThat(again.assignedAgentId()).isEqualTo(agentId);
        String otp = orderService.getOrderByToken(UUID.fromString(again.trackingToken()), customerId)
                .orElseThrow().deliveryOtp();
        assertThat(orderService.confirmDelivery(id, agentId, otp).status()).isEqualTo("DELIVERED");
    }

    @Test
    void failureNeedsAReasonAndTheAssignedRider() {
        ProductVariantDto variant = buyableVariant();
        Long agentId = newAgent("fail-rider-3");
        Long stranger = newAgent("fail-rider-4");
        Long id = dispatch(variant, agentId, "fail-key-3");

        assertThatThrownBy(() -> orderService.failDelivery(id, agentId, "  "))
                .as("a failed attempt without a reason is useless to staff")
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> orderService.failDelivery(id, stranger, "Wrong address"))
                .as("only the rider holding the order may report on it")
                .isInstanceOf(AccessDeniedException.class);
        // And a failed order cannot be marked delivered without going back out first.
        orderService.failDelivery(id, agentId, "Wrong address");
        assertThatThrownBy(() -> orderService.transition(id, new TransitionRequest("DELIVERED", "000000", null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyAnOutForDeliveryOrderCanFail() {
        ProductVariantDto variant = buyableVariant();
        Long agentId = newAgent("fail-rider-5");
        CartDto cart = cart(variant);
        Long id = orderService.placeOrder(request(cart.cartId()), "fail-key-5", null).id();
        orderService.assignAgent(id, agentId);
        // Still CONFIRMED — nothing has left the store, so there is no attempt to fail.
        assertThatThrownBy(() -> orderService.failDelivery(id, agentId, "Customer not reachable"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- helpers -----------------------------------------------------------

    private Long dispatch(ProductVariantDto variant, Long agentId, String key) {
        Long id = orderService.placeOrder(request(cart(variant).cartId()), key, null).id();
        orderService.assignAgent(id, agentId);
        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, null));
        return id;
    }

    private Long newAgent(String slug) {
        return authService.createDeliveryAgent(new CreateDeliveryAgentRequest(
                "Rider " + slug, slug + "@townbasket.local", "password123")).id();
    }

    private CartDto cart(ProductVariantDto v) {
        UUID cartId = cartService.createCart().cartId();
        return cartService.addItem(cartId, v.id(), QTY);
    }

    /** Price clears the store minimum AND stock covers the quantity (shared test DB). */
    private ProductVariantDto buyableVariant() {
        for (ProductDto p : catalogService.listProducts(null, false, null, PageRequest.of(0, 200)).content()) {
            for (ProductVariantDto v : p.variants()) {
                if (v.available() && v.availableStock() >= QTY * 4
                        && v.sellingPrice().compareTo(BigDecimal.valueOf(120)) >= 0) {
                    return v;
                }
            }
        }
        throw new IllegalStateException("No seeded variant with price >= 120 and enough stock");
    }

    private static PlaceOrderRequest request(UUID cartId) {
        return new PlaceOrderRequest(cartId, "Asha Rao", "9999900000",
                new AddressDto("12 MG Road", STORE_LAT, STORE_LNG), PaymentMethod.COD, null, null);
    }
}
