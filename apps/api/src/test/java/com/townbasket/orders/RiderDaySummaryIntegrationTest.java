package com.townbasket.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.identity.AuthService;
import com.townbasket.identity.CreateDeliveryAgentRequest;
import com.townbasket.identity.PhoneVerifyRequest;
import com.townbasket.payments.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * The rider app's "Completed" view: the orders one rider has delivered, and
 * their tally for the day — deliveries made and Pay-on-Delivery cash taken at
 * the door.
 *
 * <p>"Today" is the REAL store day, not the test suite's pinned clock: the
 * DELIVERED events are stamped with the wall clock when the rider confirms,
 * so the summary is asked about the IST date those stamps actually fall on.
 * The service only uses the application clock for its zone, which the pinned
 * test clock shares with production.
 */
class RiderDaySummaryIntegrationTest extends AbstractIntegrationTest {

    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;
    private static final int QTY = 5;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired CatalogService catalogService;
    @Autowired AuthService authService;

    @Test
    void theTallyIsThisRidersDeliveriesTodayAndOnlyTheCashOnes() {
        Long rider = newAgent("tally");
        Long otherRider = newAgent("tally-other");
        // Token reads (to fetch the OTP the way the customer sees it) are owner-scoped.
        Long customer = authService.phoneVerify(new PhoneVerifyRequest("dev:9990007001")).user().id();
        ProductVariantDto variant = buyableVariant();

        OrderDto cash1 = deliver(dispatch(variant, rider, PaymentMethod.COD, customer), rider, customer);
        OrderDto cash2 = deliver(dispatch(variant, rider, PaymentMethod.COD, customer), rider, customer);
        // Paid online before the rider ever saw it: a delivery, but no cash changed hands.
        OrderDto prepaid = deliver(dispatch(variant, rider, PaymentMethod.UPI, customer), rider, customer);
        // Still in the bag: not delivered, so not in the tally or the completed list.
        Long stillOut = dispatch(variant, rider, PaymentMethod.COD, customer);
        // Someone else's delivery on the same day must not leak into this rider's numbers.
        OrderDto theirs = deliver(dispatch(variant, otherRider, PaymentMethod.COD, customer), otherRider, customer);

        LocalDate today = LocalDate.now(IST);

        AgentDaySummary mine = orderService.agentDaySummary(rider, today);
        assertThat(mine.date()).isEqualTo(today);
        assertThat(mine.deliveredCount()).isEqualTo(3);
        assertThat(mine.codOrders()).isEqualTo(2);
        assertThat(mine.codCollected())
                .as("only the two cash orders, at their tax-inclusive totals")
                .isEqualByComparingTo(cash1.total().add(cash2.total()));
        assertThat(prepaid.paymentMethod()).isEqualTo("UPI");

        AgentDaySummary theirDay = orderService.agentDaySummary(otherRider, today);
        assertThat(theirDay.deliveredCount()).isEqualTo(1);
        assertThat(theirDay.codCollected()).isEqualByComparingTo(theirs.total());

        // A day with nothing is zeros, never null — the app renders it directly.
        AgentDaySummary yesterday = orderService.agentDaySummary(rider, today.minusDays(1));
        assertThat(yesterday.deliveredCount()).isZero();
        assertThat(yesterday.codOrders()).isZero();
        assertThat(yesterday.codCollected()).isEqualByComparingTo(BigDecimal.ZERO);

        // The completed list is the same set of orders, each carrying the moment
        // it was delivered on its timeline (the app shows that time, not placed-at).
        List<OrderDto> completed = orderService
                .listAgentOrders(rider, "DELIVERED", PageRequest.of(0, 50)).content();
        assertThat(completed).extracting(OrderDto::id)
                .contains(cash1.id(), cash2.id(), prepaid.id())
                .doesNotContain(stillOut, theirs.id());
        assertThat(completed).allSatisfy(o -> {
            assertThat(o.status()).isEqualTo("DELIVERED");
            assertThat(o.timeline()).extracting(OrderTimelineEntryDto::toStatus).contains("DELIVERED");
            assertThat(o.deliveryOtp()).as("the agent surface never carries the OTP").isNull();
        });
        // Cash orders are marked PAID at the door, which is what the card's "Collected" badge reads.
        assertThat(completed).filteredOn(o -> o.id().equals(cash1.id()))
                .singleElement().extracting(OrderDto::paymentStatus).isEqualTo("PAID");
    }

    @Test
    void theStoreTallyIsEveryRidersDeliveriesTogether() {
        // The delivery app's dispatcher view: an ADMIN signing in gets the
        // STORE's numbers, not their own. They have to — an admin can never be
        // assigned an order (assignAgent requires an active DELIVERY_AGENT), so
        // their own tally is structurally ₹0 and read as "the store took
        // nothing today".
        //
        // Asserted as a DELTA on purpose. The Testcontainers database is a
        // shared singleton, so other test classes' deliveries land on the same
        // store day; an absolute count here would pass or fail depending on
        // what else ran first.
        Long riderA = newAgent("store-a");
        Long riderB = newAgent("store-b");
        Long customer = authService.phoneVerify(new PhoneVerifyRequest("dev:9990007001")).user().id();
        ProductVariantDto variant = buyableVariant();
        LocalDate today = LocalDate.now(IST);

        AgentDaySummary before = orderService.storeDaySummary(today);

        OrderDto cashA = deliver(dispatch(variant, riderA, PaymentMethod.COD, customer), riderA, customer);
        OrderDto cashB = deliver(dispatch(variant, riderB, PaymentMethod.COD, customer), riderB, customer);
        OrderDto prepaid = deliver(dispatch(variant, riderB, PaymentMethod.UPI, customer), riderB, customer);
        // Still in a bag: out for delivery is not delivered, and must not count.
        dispatch(variant, riderA, PaymentMethod.COD, customer);

        AgentDaySummary after = orderService.storeDaySummary(today);

        assertThat(after.date()).isEqualTo(today);
        assertThat(after.deliveredCount() - before.deliveredCount())
                .as("three deliveries, across two different riders")
                .isEqualTo(3);
        assertThat(after.codOrders() - before.codOrders())
                .as("the UPI order is a delivery but not cash")
                .isEqualTo(2);
        assertThat(after.codCollected().subtract(before.codCollected()))
                .as("both riders' cash added together")
                .isEqualByComparingTo(cashA.total().add(cashB.total()));
        assertThat(prepaid.paymentMethod()).isEqualTo("UPI");

        // The whole point of the view: it is more than any one rider's.
        AgentDaySummary aOnly = orderService.agentDaySummary(riderA, today);
        assertThat(aOnly.codCollected())
                .as("rider A alone accounts for only their own order")
                .isEqualByComparingTo(cashA.total());
        assertThat(after.deliveredCount()).isGreaterThan(aOnly.deliveredCount());
    }

    @Test
    void aRiderWhoHasDeliveredNothingGetsAnEmptyTally() {
        Long rider = newAgent("tally-fresh");
        AgentDaySummary summary = orderService.agentDaySummary(rider, LocalDate.now(IST));
        assertThat(summary.deliveredCount()).isZero();
        assertThat(summary.codOrders()).isZero();
        assertThat(summary.codCollected()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- helpers -----------------------------------------------------------

    /** Place → assign → CONFIRMED → PACKING → OUT_FOR_DELIVERY; returns the order id. */
    private Long dispatch(ProductVariantDto variant, Long agentId, PaymentMethod method, Long customerId) {
        UUID cartId = cartService.createCart().cartId();
        CartDto cart = cartService.addItem(cartId, variant.id(), QTY);
        Long id = orderService.placeOrder(
                new PlaceOrderRequest(cart.cartId(), "Tally Customer", "9990007001",
                        new AddressDto("12 MG Road", STORE_LAT, STORE_LNG), method, null, null),
                "tally-" + UUID.randomUUID(), customerId).id();
        orderService.assignAgent(id, agentId);
        orderService.transition(id, new TransitionRequest("CONFIRMED", null, null));
        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        orderService.transition(id, new TransitionRequest("READY_FOR_DELIVERY", null, null));
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, null));
        return id;
    }

    /** The rider confirms with the OTP the customer can see while the order is out. */
    private OrderDto deliver(Long orderId, Long agentId, Long customerId) {
        OrderDto asAgent = orderService.listAgentOrders(agentId, "OUT_FOR_DELIVERY", PageRequest.of(0, 50))
                .content().stream().filter(o -> o.id().equals(orderId)).findFirst().orElseThrow();
        String otp = orderService.getOrderByToken(UUID.fromString(asAgent.trackingToken()), customerId)
                .orElseThrow().deliveryOtp();
        return orderService.confirmDelivery(orderId, agentId, otp);
    }

    private Long newAgent(String slug) {
        return authService.createDeliveryAgent(new CreateDeliveryAgentRequest(
                "Rider " + slug,
                slug + "-" + UUID.randomUUID().toString().substring(0, 8) + "@townbasket.local",
                "password123")).id();
    }

    /** Price clears the store minimum AND stock covers every order this test places (shared test DB). */
    private ProductVariantDto buyableVariant() {
        for (ProductDto p : catalogService.listProducts(null, false, null, PageRequest.of(0, 200)).content()) {
            for (ProductVariantDto v : p.variants()) {
                if (v.available() && v.availableStock() >= QTY * 8
                        && v.sellingPrice().compareTo(BigDecimal.valueOf(120)) >= 0) {
                    return v;
                }
            }
        }
        throw new IllegalStateException("No seeded variant with price >= 120 and enough stock");
    }
}
