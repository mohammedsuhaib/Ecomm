package com.townbasket.orders.internal;

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
import com.townbasket.orders.AddressDto;
import com.townbasket.orders.OrderDto;
import com.townbasket.orders.OrderService;
import com.townbasket.orders.PlaceOrderRequest;
import com.townbasket.orders.RiderLocationDto;
import com.townbasket.orders.TransitionRequest;
import com.townbasket.payments.PaymentMethod;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * The customer's live view of their rider, against a real Postgres: who may
 * see a rider's position, when, and for how long after the last ping.
 *
 * <p>Sits in {@code orders.internal} — not beside the other order tests in
 * {@code orders} — so it can reach the package-private
 * {@link AgentLocationRepository} and write a fix with an OLD timestamp. The
 * test clock is fixed (see {@link AbstractIntegrationTest}), so staleness
 * cannot be produced by waiting; it has to be planted.
 */
class RiderLocationIntegrationTest extends AbstractIntegrationTest {

    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;
    private static final int QTY = 5;

    /** A fix a few streets away from the address, well inside the 5 km radius. */
    private static final double RIDER_LAT = 12.2170;
    private static final double RIDER_LNG = 76.8930;

    @Autowired OrderService orderService;
    @Autowired AgentLocationRepository agentLocations;
    @Autowired CartService cartService;
    @Autowired CatalogService catalogService;
    @Autowired AuthService authService;
    @Autowired Clock clock;

    @Test
    void positionIsShownOnlyWhileOutForDelivery() {
        Long customerId = customer("dev:9990006001");
        Long agentId = newAgent("rloc-rider-1");
        OrderDto placed = place(customerId, "rloc-key-1");
        orderService.assignAgent(placed.id(), agentId);
        orderService.recordAgentLocation(agentId, RIDER_LAT, RIDER_LNG, 12.5);

        // Assigned but not yet driving to this customer: nothing to watch.
        assertThat(track(placed, customerId).riderLocation())
                .as("PLACED: assigned rider is not on the way yet").isNull();
        orderService.transition(placed.id(), new TransitionRequest("CONFIRMED", null, null));
        orderService.transition(placed.id(), new TransitionRequest("PACKING", null, null));
        assertThat(track(placed, customerId).riderLocation())
                .as("PACKING: still in the shop").isNull();

        orderService.transition(placed.id(), new TransitionRequest("OUT_FOR_DELIVERY", null, null));
        RiderLocationDto shown = track(placed, customerId).riderLocation();
        assertThat(shown).as("OUT_FOR_DELIVERY: the customer sees the rider").isNotNull();
        assertThat(shown.lat()).isEqualTo(RIDER_LAT);
        assertThat(shown.lng()).isEqualTo(RIDER_LNG);
        assertThat(shown.recordedAt()).isEqualTo(Instant.now(clock));

        // Handed over: the rider's whereabouts are no longer this customer's business.
        String otp = track(placed, customerId).deliveryOtp();
        orderService.confirmDelivery(placed.id(), agentId, otp);
        assertThat(track(placed, customerId).riderLocation())
                .as("DELIVERED: nothing left to watch").isNull();
    }

    @Test
    void aStaleFixIsHiddenUntilTheRiderReportsAgain() {
        Long customerId = customer("dev:9990006002");
        Long agentId = newAgent("rloc-rider-2");
        OrderDto order = dispatch(customerId, agentId, "rloc-key-2");

        // A phone that went quiet ten minutes ago: the last dot must not sit on
        // the customer's map looking live.
        Instant tenMinutesAgo = Instant.now(clock).minus(Duration.ofMinutes(10));
        agentLocations.upsert(agentId, RIDER_LAT, RIDER_LNG, null, tenMinutesAgo);
        assertThat(track(order, customerId).riderLocation())
                .as("a ten-minute-old fix reads as no location").isNull();

        // The next ping brings it back.
        orderService.recordAgentLocation(agentId, RIDER_LAT, RIDER_LNG, null);
        assertThat(track(order, customerId).riderLocation()).isNotNull();
    }

    @Test
    void neverLeavesTheCustomersTrackingRead() {
        Long customerId = customer("dev:9990006003");
        Long agentId = newAgent("rloc-rider-3");
        OrderDto order = dispatch(customerId, agentId, "rloc-key-3");
        orderService.recordAgentLocation(agentId, RIDER_LAT, RIDER_LNG, null);
        // Precondition: the tracking read does carry it.
        assertThat(track(order, customerId).riderLocation()).isNotNull();

        PageRequest page = PageRequest.of(0, 100);
        // The admin queue and the rider's own queue: staff and riders have the
        // rider's phone number if they need them; the customer's map is not theirs.
        assertThat(find(orderService.listOrders("OUT_FOR_DELIVERY", page).content(), order).riderLocation())
                .as("admin surface").isNull();
        assertThat(find(orderService.listAgentOrders(agentId, "OUT_FOR_DELIVERY", page).content(), order).riderLocation())
                .as("delivery surface").isNull();
        // The customer's own order HISTORY, too — a list read, kept to one
        // query; only the single-order tracking page pays for the lookup.
        assertThat(find(orderService.listUserOrders(customerId, page).content(), order).riderLocation())
                .as("order history is a list, not the tracking page").isNull();
    }

    @Test
    void onlyTheAssignedRidersPositionIsShown() {
        Long customerId = customer("dev:9990006004");
        Long mine = newAgent("rloc-rider-4");
        Long someoneElse = newAgent("rloc-rider-5");
        OrderDto order = dispatch(customerId, mine, "rloc-key-4");

        // Another rider is out on the road too; their dot is not this order's.
        orderService.recordAgentLocation(someoneElse, RIDER_LAT, RIDER_LNG, null);
        assertThat(track(order, customerId).riderLocation()).isNull();

        orderService.recordAgentLocation(mine, RIDER_LAT + 0.001, RIDER_LNG, null);
        assertThat(track(order, customerId).riderLocation().lat()).isEqualTo(RIDER_LAT + 0.001);
    }

    @Test
    void stoppingSharingForgetsThePosition() {
        Long customerId = customer("dev:9990006005");
        Long agentId = newAgent("rloc-rider-6");
        OrderDto order = dispatch(customerId, agentId, "rloc-key-5");
        orderService.recordAgentLocation(agentId, RIDER_LAT, RIDER_LNG, null);
        assertThat(track(order, customerId).riderLocation()).isNotNull();

        orderService.clearAgentLocation(agentId);
        assertThat(track(order, customerId).riderLocation()).isNull();
        assertThat(agentLocations.findById(agentId)).as("the row is gone, not just hidden").isEmpty();
        // Idempotent: clearing again is not an error.
        orderService.clearAgentLocation(agentId);
    }

    @Test
    void refusesAFixOffTheGlobe() {
        Long agentId = newAgent("rloc-rider-7");
        assertThatThrownBy(() -> orderService.recordAgentLocation(agentId, 91, RIDER_LNG, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.recordAgentLocation(agentId, RIDER_LAT, -181, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.recordAgentLocation(agentId, Double.NaN, RIDER_LNG, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.recordAgentLocation(agentId, RIDER_LAT, RIDER_LNG, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(agentLocations.findById(agentId)).as("nothing was stored").isEmpty();
    }

    // ---- helpers -----------------------------------------------------------

    private OrderDto track(OrderDto order, Long customerId) {
        return orderService.getOrderByToken(UUID.fromString(order.trackingToken()), customerId).orElseThrow();
    }

    private static OrderDto find(Iterable<OrderDto> orders, OrderDto wanted) {
        for (OrderDto o : orders) {
            if (o.id().equals(wanted.id())) {
                return o;
            }
        }
        throw new AssertionError("order " + wanted.publicCode() + " missing from the list");
    }

    /** Placed, assigned and sent out — the state in which a customer watches the rider. */
    private OrderDto dispatch(Long customerId, Long agentId, String key) {
        OrderDto placed = place(customerId, key);
        orderService.assignAgent(placed.id(), agentId);
        orderService.transition(placed.id(), new TransitionRequest("CONFIRMED", null, null));
        orderService.transition(placed.id(), new TransitionRequest("PACKING", null, null));
        return orderService.transition(placed.id(), new TransitionRequest("OUT_FOR_DELIVERY", null, null));
    }

    private OrderDto place(Long customerId, String key) {
        return orderService.placeOrder(request(cart(buyableVariant()).cartId()), key, customerId);
    }

    /** Token reads are owner-scoped, so every tracked order needs a real customer. */
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
