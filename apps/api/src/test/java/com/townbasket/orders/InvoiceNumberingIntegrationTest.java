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
import com.townbasket.payments.PaymentMethod;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * The two customer-visible order identifiers, against a real Postgres: the
 * short order code every order gets at checkout, and the GST invoice number
 * issued from a per-financial-year consecutive series.
 *
 * <p>The Testcontainers Postgres is shared across test classes, so other tests
 * place orders and issue invoices too. Nothing here asserts an absolute
 * sequence number — only the shape of a number and how two of them relate.
 */
class InvoiceNumberingIntegrationTest extends AbstractIntegrationTest {

    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;
    private static final int QTY = 5;

    /**
     * The financial year of the fixed test clock (2024-01-01 IST): a January
     * date belongs to the year that started the previous April.
     */
    private static final String TEST_FY = "23-24";

    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired CatalogService catalogService;
    @Autowired AuthService authService;

    /**
     * A rider to pin a delivery on. DELIVERED requires an assigned agent — the
     * delivery and any cash collected are booked against them — so even a test
     * about invoice numbers has to walk the real flow. The email is unique per
     * call because the Testcontainers database is a shared singleton and
     * {@code deliver()} runs more than once per test run.
     */
    private Long newRider() {
        return authService.createDeliveryAgent(new CreateDeliveryAgentRequest(
                "Invoice Rider",
                "invoice-rider-" + System.nanoTime() + "@townbasket.local",
                "password123")).id();
    }

    // ---- order code --------------------------------------------------------

    @Test
    void everyOrderGetsItsOwnSpeakableCode() {
        Long customer = customer("9991110001");
        OrderDto first = place(customer, "code-key-1");
        OrderDto second = place(customer, "code-key-2");

        // Crockford base32, so no I/L/O/U to be misheard over the phone.
        assertThat(first.publicCode()).containsPattern("^[0-9A-HJKMNP-TV-Z]{8}$");
        assertThat(second.publicCode()).containsPattern("^[0-9A-HJKMNP-TV-Z]{8}$");
        assertThat(first.publicCode()).isNotEqualTo(second.publicCode());

        // The code is NOT the sequential id in disguise: that is the whole point
        // of having it, since the id publishes the store's order volume.
        assertThat(first.publicCode()).isNotEqualTo(String.valueOf(first.id()));
    }

    @Test
    void staffCanFindAnOrderByTheCodeTheCustomerDictates() {
        Long customer = customer("9991110002");
        OrderDto order = place(customer, "code-key-3");
        String code = order.publicCode();

        // Exactly as stored, and as a flustered customer actually says it:
        // lower case, with a hyphen in the middle.
        for (String typed : new String[] {
                code,
                code.toLowerCase(),
                code.substring(0, 4) + "-" + code.substring(4),
        }) {
            assertThat(orderService.listOrders(null, typed, PageRequest.of(0, 50)).content())
                    .as("searching for \"%s\"", typed)
                    .extracting(OrderDto::id)
                    .contains(order.id());
        }
    }

    // ---- invoice number ----------------------------------------------------

    @Test
    void invoiceNumberIsIssuedOnceInTheCurrentFinancialYear() {
        Long customer = customer("9991110003");
        OrderDto order = place(customer, "inv-key-1");
        UUID token = UUID.fromString(order.trackingToken());

        // Not billed until an invoice is actually asked for.
        assertThat(order.invoiceNumber()).isNull();
        assertThat(order.invoicedAt()).isNull();

        deliver(order, customer);
        OrderDto issued = orderService.issueInvoice(token, customer);
        assertThat(issued.invoiceNumber()).startsWith("TB/" + TEST_FY + "/");
        assertThat(issued.invoiceNumber().length()).isLessThanOrEqualTo(16);
        assertThat(issued.invoicedAt()).isNotNull();

        // Re-downloading reproduces the SAME document rather than billing one
        // supply twice.
        OrderDto again = orderService.issueInvoice(token, customer);
        assertThat(again.invoiceNumber()).isEqualTo(issued.invoiceNumber());
        // Compared at second precision: Postgres TIMESTAMPTZ keeps microseconds
        // while Instant carries nanoseconds, so a re-read can differ below the
        // microsecond without the stamp having been rewritten.
        assertThat(again.invoicedAt().getEpochSecond())
                .isEqualTo(issued.invoicedAt().getEpochSecond());

        // And the number sticks to the order on a plain read.
        assertThat(orderService.getOrderByToken(token, customer).orElseThrow().invoiceNumber())
                .isEqualTo(issued.invoiceNumber());
    }

    @Test
    void theSeriesIsConsecutiveWithinItsFinancialYear() {
        Long customer = customer("9991110004");
        OrderDto first = place(customer, "inv-key-2");
        OrderDto second = place(customer, "inv-key-3");
        deliver(first, customer);
        deliver(second, customer);

        long a = sequenceOf(orderService
                .issueInvoice(UUID.fromString(first.trackingToken()), customer).invoiceNumber());
        long b = sequenceOf(orderService
                .issueInvoice(UUID.fromString(second.trackingToken()), customer).invoiceNumber());

        // Consecutive, with no gap — what Rule 46(b) asks for. Absolute values
        // depend on what other tests issued against this shared database; the
        // exact +1 holds because the suite runs sequentially (no parallel
        // surefire/JUnit configuration), so nothing issues between these two.
        assertThat(b).isEqualTo(a + 1);
    }

    @Test
    void noInvoiceUntilTheGoodsAreActuallyHandedOver() {
        Long customer = customer("9991110008");
        OrderDto order = place(customer, "inv-key-6");
        UUID token = UUID.fromString(order.trackingToken());
        Long id = order.id();

        // Every state before handover: the goods are still the store's, on a
        // shelf, in a crate, or in a bag on a bike.
        assertThat(order.status()).isEqualTo("PLACED");
        assertRefusedBeforeDelivery(token, customer);

        orderService.transition(id, new TransitionRequest("CONFIRMED", null, null));
        assertRefusedBeforeDelivery(token, customer);

        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        assertRefusedBeforeDelivery(token, customer);

        orderService.assignAgent(id, newRider());
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, null));
        assertRefusedBeforeDelivery(token, customer);

        // A failed attempt brings the goods back, so it is still not a supply.
        orderService.transition(id, new TransitionRequest("DELIVERY_FAILED", null, "Nobody home"));
        assertRefusedBeforeDelivery(token, customer);

        // None of those refusals may have consumed a number from the series.
        assertThat(orderService.getOrderByToken(token, customer).orElseThrow().invoiceNumber())
                .isNull();

        // Second attempt lands: now there is a supply to bill.
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, "Second attempt"));
        String otp = orderService.getOrderByToken(token, customer).orElseThrow().deliveryOtp();
        orderService.transition(id, new TransitionRequest("DELIVERED", otp, null));

        assertThat(orderService.issueInvoice(token, customer).invoiceNumber())
                .startsWith("TB/" + TEST_FY + "/");
    }

    @Test
    void aCancelledOrderHasNoInvoice() {
        Long customer = customer("9991110005");
        OrderDto order = place(customer, "inv-key-4");
        UUID token = UUID.fromString(order.trackingToken());

        orderService.cancelByToken(token, customer);

        // No supply, so nothing to bill — and no void document dropped into a
        // series that is meant to record goods actually supplied.
        assertThatThrownBy(() -> orderService.issueInvoice(token, customer))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void issuingAnInvoiceIsOwnerScoped() {
        Long owner = customer("9991110006");
        Long stranger = customer("9991110007");
        UUID token = UUID.fromString(place(owner, "inv-key-5").trackingToken());

        // Same access model as tracking: a non-owner, and an anonymous caller,
        // get "no such order" rather than a 403 that would confirm it exists.
        assertThatThrownBy(() -> orderService.issueInvoice(token, stranger))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orderService.issueInvoice(token, null))
                .isInstanceOf(ResourceNotFoundException.class);

        // A stranger's failed attempt must not have consumed a number.
        assertThat(orderService.getOrderByToken(token, owner).orElseThrow().invoiceNumber())
                .isNull();

        // Ownership is checked BEFORE the delivered-yet check, so the refusal a
        // stranger sees is identical either way — they can't learn an order's
        // status (or that it exists) by watching which error comes back.
        OrderDto delivered = place(owner, "inv-key-7");
        deliver(delivered, owner);
        UUID deliveredToken = UUID.fromString(delivered.trackingToken());
        assertThatThrownBy(() -> orderService.issueInvoice(deliveredToken, stranger))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- helpers -----------------------------------------------------------

    /** An undelivered order is refused, and told when its invoice will exist. */
    private void assertRefusedBeforeDelivery(UUID token, Long userId) {
        assertThatThrownBy(() -> orderService.issueInvoice(token, userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("delivered");
    }

    /** Walk an order to DELIVERED — an invoice needs a supply to have happened. */
    private void deliver(OrderDto order, Long ownerId) {
        Long id = order.id();
        orderService.transition(id, new TransitionRequest("CONFIRMED", null, null));
        orderService.transition(id, new TransitionRequest("PACKING", null, null));
        orderService.assignAgent(id, newRider());
        orderService.transition(id, new TransitionRequest("OUT_FOR_DELIVERY", null, null));
        // The OTP reaches the customer only at OUT_FOR_DELIVERY, and only the owner.
        String otp = orderService.getOrderByToken(UUID.fromString(order.trackingToken()), ownerId)
                .orElseThrow().deliveryOtp();
        orderService.transition(id, new TransitionRequest("DELIVERED", otp, null));
    }

    /** The trailing sequence of an invoice number like {@code TB/23-24/00007}. */
    private static long sequenceOf(String invoiceNumber) {
        return Long.parseLong(invoiceNumber.substring(invoiceNumber.lastIndexOf('/') + 1));
    }

    private Long customer(String phone10) {
        return authService.phoneVerify(new PhoneVerifyRequest("dev:" + phone10)).user().id();
    }

    private OrderDto place(Long userId, String idempotencyKey) {
        ProductVariantDto variant = buyableVariant();
        CartDto cart = cartService.addItem(cartService.createCart().cartId(), variant.id(), QTY);
        return orderService.placeOrder(
                new PlaceOrderRequest(cart.cartId(), "Asha Rao", "9999900000",
                        new AddressDto("12 MG Road", STORE_LAT, STORE_LNG),
                        PaymentMethod.COD, null, null),
                idempotencyKey, userId);
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
}
