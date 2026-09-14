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
import com.townbasket.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

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

    /** Units per test order — enough of a ₹120+ variant to clear the ₹299 minimum. */
    private static final int QTY = 5;

    @Autowired
    OrderService orderService;
    @Autowired
    CartService cartService;
    @Autowired
    CatalogService catalogService;
    @Autowired
    InventoryService inventoryService;
    @Autowired
    AuthService authService;
    @Autowired
    JdbcTemplate jdbc;

    /**
     * A variant that is priced high enough to clear the minimum order value AND
     * still has room for the orders this class places.
     *
     * <p>The stock requirement is not belt-and-braces, it is the reason this
     * method exists in this form. Integration tests share ONE Postgres for the
     * whole suite ({@link com.townbasket.AbstractIntegrationTest} keeps the
     * container static and never stops it), and nothing resets inventory between
     * classes. Seven classes place orders against the first variant priced over
     * ₹120, and an order left CONFIRMED holds its reservation for the life of the
     * suite, so `available` on that one variant only ever falls. Without this
     * check the class quietly depends on how much the classes before it happened
     * to consume. main was green; this branch added two more orders and six
     * tests in this class then errored at "requested 5, available 4" — four
     * units left of a hundred, so the margin had been thin for a while rather
     * than the new orders being unreasonable. Asking for headroom makes each
     * test roll onto a variant that can actually satisfy it, which is what the
     * four sibling classes that hit this first already do.
     */
    private ProductVariantDto pickPricyVariant() {
        for (ProductDto p : catalogService.listProducts(null, false, null, PageRequest.of(0, 200)).content()) {
            for (ProductVariantDto v : p.variants()) {
                if (v.available() && v.availableStock() >= QTY * 4
                        && v.sellingPrice().compareTo(BigDecimal.valueOf(120)) >= 0) {
                    return v;
                }
            }
        }
        throw new IllegalStateException(
                "No seeded variant with price >= 120 and enough stock left in the shared database");
    }

    private CartDto cartWithValue(ProductVariantDto variant, int qty) {
        UUID cartId = cartService.createCart().cartId();
        return cartService.addItem(cartId, variant.id(), qty);
    }

    private PlaceOrderRequest request(UUID cartId, PaymentMethod method) {
        return new PlaceOrderRequest(
                cartId, "Asha Rao", "9999900000",
                new AddressDto("12 MG Road", STORE_LAT, STORE_LNG),
                method, null, null);
    }

    /** Sign in (upsert) a CUSTOMER via the dev/fake phone verifier and return the user id. */
    private Long customer(String phone10) {
        return authService.phoneVerify(new PhoneVerifyRequest("dev:" + phone10)).user().id();
    }

    @Test
    void codCheckoutReservesStockConfirmsAndHidesOtpAndCostPrice() {
        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());

        CartDto cart = cartWithValue(variant, QTY);
        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "cod-key-1", null);

        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(order.paymentMethod()).isEqualTo("COD");
        assertThat(order.paymentStatus()).isEqualTo("COD_PENDING");
        // The customer gets an unguessable tracking token, NOT a guessable id...
        assertThat(order.trackingToken()).isNotBlank();
        // ...and the delivery OTP stays hidden until the order is OUT_FOR_DELIVERY.
        assertThat(order.deliveryOtp()).isNull();
        assertThat(order.items()).allSatisfy(i -> assertThat(i.unitPrice()).isNotNull());
        // OrderItemDto has no cost-price accessor at all (compile-time guarantee).
        assertThat(order.timeline()).extracting(OrderTimelineEntryDto::toStatus)
                .containsExactly("PLACED", "CONFIRMED");

        // GST snapshot: prices are tax-INCLUSIVE, so each line's breakdown
        // re-adds to its line total and the order total is untouched by tax.
        assertThat(order.items()).allSatisfy(i -> {
            assertThat(i.gstRatePercent()).isNotNull();
            assertThat(i.taxableValue().add(i.cgst()).add(i.sgst()))
                    .isEqualByComparingTo(i.lineTotal());
        });
        BigDecimal itemTax = order.items().stream()
                .map(i -> i.cgst().add(i.sgst()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(order.totalTax()).isEqualByComparingTo(itemTax);

        // Stock reserved -> availability dropped by 5.
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - 5);
    }

    @Test
    void upiFakeCheckoutConfirmsAndMarksPaid() {
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, QTY);

        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.UPI), "upi-key-1", null);

        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(order.paymentMethod()).isEqualTo("UPI");
        assertThat(order.paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void checkoutIsIdempotentOnKey() {
        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());
        CartDto cart = cartWithValue(variant, QTY);

        OrderDto first = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "idem-key-1", null);
        OrderDto second = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "idem-key-1", null);

        assertThat(second.id()).isEqualTo(first.id());
        // Stock reserved only once.
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - 5);
    }

    @Test
    void sameCartCannotBeOrderedTwice() {
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, QTY);
        orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "dup-key-1", null);

        // A second checkout of the SAME cart under a DIFFERENT idempotency key is
        // rejected because the cart is now checked out. (The DB unique index on
        // orders.cart_id is the backstop for the concurrent-race variant, where
        // both requests pass the in-transaction checkedOut check before committing.)
        assertThatThrownBy(() -> orderService.placeOrder(
                request(cart.cartId(), PaymentMethod.COD), "dup-key-2", null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void belowMinimumOrderValueIsRejected() {
        // Cheapest single unit (≤ ₹40) is well below the ₹299 minimum.
        ProductVariantDto cheap = catalogService.listProducts(null, false, null, PageRequest.of(0, 200)).content().stream()
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
        CartDto cart = cartWithValue(variant, QTY);
        PlaceOrderRequest req = new PlaceOrderRequest(
                cart.cartId(), "Asha Rao", "9999900000",
                new AddressDto("Far away", 12.2958, 76.6394), // ~25 km
                PaymentMethod.COD, null, null);

        assertThatThrownBy(() -> orderService.placeOrder(req, "radius-key-1", null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adminTransitionFlowDeliveredRequiresMatchingOtp() {
        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());
        CartDto cart = cartWithValue(variant, QTY);
        Long customerId = customer("9990001111");
        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "flow-key-1", customerId);

        Long id = order.id();
        // Assign a rider so the delivery lands in the per-agent stats.
        Long agentId = authService.createDeliveryAgent(new CreateDeliveryAgentRequest(
                "Stats Rider", "stats-rider@townbasket.local", "password123")).id();
        orderService.assignAgent(id, agentId);
        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, null));

        // The OTP is exposed to the customer only now (OUT_FOR_DELIVERY) — and
        // only to the OWNER: token reads are scoped to the placing account.
        String otp = orderService.getOrderByToken(UUID.fromString(order.trackingToken()), customerId)
                .orElseThrow().deliveryOtp();
        assertThat(otp).hasSize(6);

        // Wrong OTP rejected.
        assertThatThrownBy(() -> orderService.transition(id, new TransitionRequest("DELIVERED", "000000", null)))
                .isInstanceOf(BusinessRuleException.class);

        // Correct OTP delivers; COD becomes PAID.
        OrderDto delivered = orderService.transition(id, new TransitionRequest("DELIVERED", otp, null));
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.paymentStatus()).isEqualTo("PAID");

        // OrderDelivered is consumed by inventory (async, after commit) to COMMIT
        // the reservation: on_hand drops, so available stays reduced by 5.
        eventually(() -> assertThat(inventoryService.availability(variant.id())).isEqualTo(before - 5));

        // The delivery shows up in the per-agent date-wise stats, with the
        // order's value summed in (>= because other tests may add deliveries).
        // The report is windowed now; 30 days comfortably covers a delivery the
        // fixed test clock just recorded.
        assertThat(orderService.deliveryStatsByAgent(30))
                .anySatisfy(s -> {
                    assertThat(s.agentId()).isEqualTo(agentId);
                    assertThat(s.deliveries()).isGreaterThanOrEqualTo(1);
                    assertThat(s.amount()).isGreaterThanOrEqualTo(delivered.total());
                });
    }

    @Test
    void concurrentDeliveredTransitionsCommitExactlyOnce() throws Exception {
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, QTY);
        Long customerId = customer("9990002222");
        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "race-key-1", customerId);
        Long id = order.id();
        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, null));
        String otp = orderService.getOrderByToken(UUID.fromString(order.trackingToken()), customerId)
                .orElseThrow().deliveryOtp();

        // A rider double-tapping "confirm delivery" on a flaky connection: two
        // identical DELIVERED transitions race. Exactly one may commit — the
        // loser fails (optimistic lock / unique index / illegal-transition check,
        // depending on interleaving) and its transaction rolls back, taking the
        // duplicate event row AND the duplicate outbox publications (inventory
        // commit, COD->PAID, notifications) with it.
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Boolean> deliver = () -> {
            barrier.await();
            try {
                orderService.transition(id, new TransitionRequest("DELIVERED", otp, null));
                return true;
            } catch (Exception e) {
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = pool.invokeAll(List.of(deliver, deliver));
            int successes = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdown();
        }

        OrderDto after = orderService
                .getOrderByToken(UUID.fromString(order.trackingToken()), customerId)
                .orElseThrow();
        assertThat(after.status()).isEqualTo("DELIVERED");
        assertThat(after.paymentStatus()).isEqualTo("PAID");
        // Exactly one DELIVERED row in the timeline (= orders.order_events).
        assertThat(after.timeline()).extracting(OrderTimelineEntryDto::toStatus)
                .filteredOn("DELIVERED"::equals)
                .hasSize(1);
    }

    @Test
    void illegalTransitionIsRejected() {
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, QTY);
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
        CartDto cart = cartWithValue(variant, QTY);

        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "cancel-key-1", null);
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before - 5);

        orderService.transition(order.id(), new TransitionRequest("CANCELLED", null, "Customer changed mind"));

        // OrderCancelled is consumed by inventory (async, after commit) to RELEASE
        // the reservation -> availability restored.
        eventually(() -> assertThat(inventoryService.availability(variant.id())).isEqualTo(before));
    }

    @Test
    void trackingIsOwnerScoped_notAnonymousAndNotAnotherUser() {
        ProductVariantDto variant = pickPricyVariant();
        Long owner = customer("9990003333");
        Long stranger = customer("9990004444");
        CartDto cart = cartWithValue(variant, QTY);
        OrderDto order = orderService.placeOrder(request(cart.cartId(), PaymentMethod.COD), "own-key-1", owner);
        UUID token = UUID.fromString(order.trackingToken());

        // The unguessable token alone is NOT a capability: no user, wrong user,
        // and an ownerless (legacy guest) order all read as "no such order".
        assertThat(orderService.getOrderByToken(token, owner)).isPresent();
        assertThat(orderService.getOrderByToken(token, null)).isEmpty();
        assertThat(orderService.getOrderByToken(token, stranger)).isEmpty();

        // Same for self-service cancel — a stranger can't cancel someone
        // else's order, and the refusal never confirms the order exists.
        assertThatThrownBy(() -> orderService.cancelByToken(token, stranger))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orderService.cancelByToken(token, null))
                .isInstanceOf(ResourceNotFoundException.class);

        // The SSE-stream ownership gate agrees.
        assertThat(orderService.isOwnedBy(order.id(), owner)).isTrue();
        assertThat(orderService.isOwnedBy(order.id(), stranger)).isFalse();
        assertThat(orderService.isOwnedBy(order.id(), null)).isFalse();

        // The owner can still self-cancel within the window.
        assertThat(orderService.cancelByToken(token, owner).status()).isEqualTo("CANCELLED");

        // An order placed with NO owner (legacy guest path) is trackable by nobody.
        OrderDto guestOrder = orderService.placeOrder(
                request(cartWithValue(variant, QTY).cartId(), PaymentMethod.COD), "own-key-2", null);
        assertThat(orderService.getOrderByToken(UUID.fromString(guestOrder.trackingToken()), owner)).isEmpty();
    }

    @Test
    void adminListReturnsNewestFirst() {
        ProductVariantDto variant = pickPricyVariant();
        OrderDto o1 = orderService.placeOrder(
                request(cartWithValue(variant, QTY).cartId(), PaymentMethod.COD), "list-key-1", null);
        OrderDto o2 = orderService.placeOrder(
                request(cartWithValue(variant, QTY).cartId(), PaymentMethod.COD), "list-key-2", null);

        List<OrderDto> all = orderService.listOrders(null, PageRequest.of(0, 50)).content();
        List<Long> ids = all.stream().map(OrderDto::id).toList();
        assertThat(ids).contains(o1.id(), o2.id());
        // Newest (o2) appears before older (o1).
        assertThat(ids.indexOf(o2.id())).isLessThan(ids.indexOf(o1.id()));
    }

    @Test
    void anOrderLineSnapshotsTheKannadaNameItWasSoldUnder() {
        // The order screen has to name the item in the language the customer
        // shopped in, and an order line is a SNAPSHOT — a later rename in the
        // catalog must not rewrite a past order — so the Kannada name is frozen
        // beside the English one at the moment of sale, not looked up on read.
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, QTY);
        Long productId = cart.items().get(0).productId();
        String english = cart.items().get(0).productName();

        jdbc.update("UPDATE catalog.products SET name_kn = ? WHERE id = ?",
                "\u0c95\u0ca8\u0ccd\u0ca8\u0ca1 \u0cb9\u0cc6\u0cb8\u0cb0\u0cc1", productId);
        try {
            // Re-read: the cart resolves names against the catalog at read time.
            assertThat(cartService.getCart(cart.cartId()).orElseThrow().items().get(0).productNameKn())
                    .isEqualTo("\u0c95\u0ca8\u0ccd\u0ca8\u0ca1 \u0cb9\u0cc6\u0cb8\u0cb0\u0cc1");

            OrderDto order = orderService.placeOrder(
                    request(cart.cartId(), PaymentMethod.COD), "kn-name-key-1", null);

            assertThat(order.items()).isNotEmpty();
            OrderItemDto line = order.items().get(0);
            assertThat(line.productNameKn())
                    .isEqualTo("\u0c95\u0ca8\u0ccd\u0ca8\u0ca1 \u0cb9\u0cc6\u0cb8\u0cb0\u0cc1");
            // The English name is still there beside it — the invoice uses that one.
            assertThat(line.productName()).isEqualTo(english);
        } finally {
            jdbc.update("UPDATE catalog.products SET name_kn = NULL WHERE id = ?", productId);
        }
    }

    @Test
    void aCancellationCarriesItsReasonToTheCustomersTimeline() {
        // The customer's order screen used to say only that the order "was
        // cancelled", never why — so the one question they have, with their money
        // involved, went unanswered even though staff had typed an answer. The
        // reason staff give is recorded on the CANCELLED timeline entry, which is
        // what that screen reads.
        ProductVariantDto variant = pickPricyVariant();
        CartDto cart = cartWithValue(variant, QTY);
        OrderDto order = orderService.placeOrder(
                request(cart.cartId(), PaymentMethod.COD), "cancel-reason-key-1", null);

        OrderDto cancelled = orderService.transition(order.id(),
                new TransitionRequest("CANCELLED", null, "Out of stock after packing"));

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.timeline())
                .filteredOn(e -> "CANCELLED".equals(e.toStatus()))
                .singleElement()
                .satisfies(e -> assertThat(e.note()).isEqualTo("Out of stock after packing"));
    }

    @Test
    void aSelfCancelRecordsTheReservedTokenRatherThanAnEnglishSentence() {
        // A self-service cancel goes through the same transition, so its "reason"
        // lands on the timeline the customer reads. The API therefore records a
        // token saying WHO cancelled, not a sentence: only the storefront knows
        // what language to say it in. (It used to store "Cancelled by customer",
        // which a Kannada customer would have been shown verbatim.)
        ProductVariantDto variant = pickPricyVariant();
        Long owner = customer("9990005555");
        CartDto cart = cartWithValue(variant, QTY);
        OrderDto order = orderService.placeOrder(
                request(cart.cartId(), PaymentMethod.COD), "self-cancel-key-1", owner);

        OrderDto cancelled =
                orderService.cancelByToken(UUID.fromString(order.trackingToken()), owner);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.timeline())
                .filteredOn(e -> "CANCELLED".equals(e.toStatus()))
                .singleElement()
                .satisfies(e -> assertThat(e.note())
                        .isEqualTo(TransitionRequest.CUSTOMER_REQUEST));
    }
}
