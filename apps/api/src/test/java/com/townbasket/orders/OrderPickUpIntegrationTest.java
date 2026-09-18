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
 * The hand-over step against a real Postgres: staff pack an order and mark it
 * READY_FOR_DELIVERY, and the RIDER moves it to OUT_FOR_DELIVERY when they
 * actually take the bag.
 *
 * <p>Two properties matter here. The store can no longer put an order on the
 * road by itself, so the queue stops claiming a bag has left while it is still
 * on the counter. And everything the customer sees of the journey — the
 * delivery code and the live position — starts at the pick-up, not at the
 * pack-up.
 */
class OrderPickUpIntegrationTest extends AbstractIntegrationTest {

    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;
    private static final int QTY = 5;

    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired CatalogService catalogService;
    @Autowired InventoryService inventoryService;
    @Autowired AuthService authService;

    @Test
    void theRiderPickingItUpIsWhatPutsItOnTheRoad() {
        ProductVariantDto variant = buyableVariant();
        Long customerId = customer("dev:9990007001");
        Long agentId = newAgent("pickup-rider-1");
        OrderDto ready = readyForDelivery(variant, customerId, agentId, "pickup-key-1");
        Long id = ready.id();
        UUID token = UUID.fromString(ready.trackingToken());

        // Waiting on the counter: no delivery code yet, because nobody is at the
        // door to give it to.
        assertThat(orderService.getOrderByToken(token, customerId).orElseThrow().deliveryOtp())
                .as("READY_FOR_DELIVERY: the code is not live yet").isNull();

        OrderDto out = orderService.pickUp(id, agentId);
        assertThat(out.status()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(out.assignedAgentId()).isEqualTo(agentId);
        // Both steps are on the record, in order: who packed it and who took it.
        assertThat(out.timeline()).extracting(OrderTimelineEntryDto::toStatus)
                .containsSubsequence("PACKING", "READY_FOR_DELIVERY", "OUT_FOR_DELIVERY");
        // ...and now the customer has a code to hand over.
        assertThat(orderService.getOrderByToken(token, customerId).orElseThrow().deliveryOtp())
                .as("OUT_FOR_DELIVERY: the code is live").isNotNull();
    }

    @Test
    void aRiderCanOnlyPickUpTheirOwnOrder() {
        ProductVariantDto variant = buyableVariant();
        Long agentId = newAgent("pickup-rider-2");
        Long stranger = newAgent("pickup-rider-3");
        Long id = readyForDelivery(variant, null, agentId, "pickup-key-2").id();

        assertThatThrownBy(() -> orderService.pickUp(id, stranger))
                .as("a bag with someone else's name on it is not theirs to take")
                .isInstanceOf(AccessDeniedException.class);
        // Untouched: still waiting for the rider it belongs to.
        assertThat(orderService.pickUp(id, agentId).status()).isEqualTo("OUT_FOR_DELIVERY");
    }

    @Test
    void anOrderStillBeingPackedCannotBeCollected() {
        ProductVariantDto variant = buyableVariant();
        Long agentId = newAgent("pickup-rider-4");
        Long id = orderService.placeOrder(request(cart(variant).cartId()), "pickup-key-3", null).id();
        orderService.assignAgent(id, agentId);
        orderService.transition(id, new TransitionRequest("CONFIRMED", null, null));
        orderService.transition(id, new TransitionRequest("PACKING", null, null));

        assertThatThrownBy(() -> orderService.pickUp(id, agentId))
                .as("there is nothing on the counter to collect yet")
                .isInstanceOf(BusinessRuleException.class);
        // And staff cannot shortcut the hand-over from the packing bench either.
        assertThatThrownBy(() -> orderService.transition(
                id, new TransitionRequest("OUT_FOR_DELIVERY", null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void collectingTwiceIsNotAnError() {
        ProductVariantDto variant = buyableVariant();
        Long agentId = newAgent("pickup-rider-5");
        Long id = readyForDelivery(variant, null, agentId, "pickup-key-4").id();

        assertThat(orderService.pickUp(id, agentId).status()).isEqualTo("OUT_FOR_DELIVERY");
        // The rider tapped it, the reply was lost in a dead zone, they tapped
        // again. They are holding the bag; the app must not tell them otherwise.
        assertThat(orderService.pickUp(id, agentId).status()).isEqualTo("OUT_FOR_DELIVERY");
        // Exactly one hand-over on the timeline, not two.
        OrderDto after = orderService.listAgentOrders(agentId, "OUT_FOR_DELIVERY", PageRequest.of(0, 50))
                .content().stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
        assertThat(after.timeline()).extracting(OrderTimelineEntryDto::toStatus)
                .filteredOn("OUT_FOR_DELIVERY"::equals).hasSize(1);
    }

    @Test
    void cancellingAnUncollectedOrderPutsTheStockBack() {
        ProductVariantDto variant = buyableVariant();
        int before = inventoryService.availability(variant.id());
        Long agentId = newAgent("pickup-rider-6");
        Long id = readyForDelivery(variant, null, agentId, "pickup-key-5").id();
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - QTY);

        // Nothing has left the shop, so this is an ordinary cancellation: the
        // bag is unpacked back onto the shelves. (Contrast DELIVERY_FAILED,
        // where the goods are on a bike and the reservation is kept.)
        assertThat(orderService.transition(id, new TransitionRequest("CANCELLED", null, "Store closing"))
                .status()).isEqualTo("CANCELLED");
        eventually(() -> assertThat(inventoryService.availability(variant.id())).isEqualTo(before));
    }

    // ---- helpers -----------------------------------------------------------

    /** Place → assign → CONFIRMED → PACKING → READY_FOR_DELIVERY. */
    private OrderDto readyForDelivery(ProductVariantDto variant, Long customerId, Long agentId, String key) {
        OrderDto placed = orderService.placeOrder(request(cart(variant).cartId()), key, customerId);
        Long id = placed.id();
        orderService.assignAgent(id, agentId);
        orderService.transition(id, new TransitionRequest("CONFIRMED", null, null));
        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        assertThat(orderService.transition(id, new TransitionRequest("READY_FOR_DELIVERY", null, null))
                .status()).isEqualTo("READY_FOR_DELIVERY");
        // The tracking token is stable for the life of the order, so the
        // placement DTO is as good a source for it as a re-read.
        return placed;
    }

    private Long customer(String devToken) {
        return authService.phoneVerify(new PhoneVerifyRequest(devToken)).user().id();
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
