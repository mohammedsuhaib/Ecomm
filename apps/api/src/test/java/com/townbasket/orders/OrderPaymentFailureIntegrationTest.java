package com.townbasket.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.inventory.InventoryService;
import com.townbasket.payments.PaymentMethod;
import com.townbasket.payments.PaymentResult;
import com.townbasket.payments.PaymentService;
import com.townbasket.payments.PaymentStatus;
import com.townbasket.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;

/**
 * Verifies the payment-failure path of checkout: a declined online payment must
 * roll the whole checkout back so no order is left holding a stock reservation.
 *
 * <p>The default {@code FakeProvider} always succeeds, so {@link PaymentService}
 * is mocked here to return {@code FAILED}; everything else (cart, inventory,
 * serviceability) runs for real against Postgres.
 */
class OrderPaymentFailureIntegrationTest extends AbstractIntegrationTest {

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

    @MockBean
    PaymentService paymentService;

    /**
     * A variant that is priced high enough to clear the minimum order value AND
     * still has room for the orders this class places.
     *
     * <p>The stock requirement is not belt-and-braces, it is the reason this
     * method exists in this form. Integration tests share ONE Postgres for the
     * whole suite ({@link com.townbasket.AbstractIntegrationTest} keeps the
     * container static and never stops it), and nothing resets inventory between
     * classes. Seven classes place orders against the first variant priced over
     * ₹120, and an order left un-cancelled holds its reservation for the life of
     * the suite, so `available` on that one variant only ever falls. Without this
     * check the class quietly depends on how much the classes before it happened
     * to consume. main was green; this branch added two more orders and six
     * tests in this class then errored at "requested 5, available 4" — four
     * units left of a hundred, so the margin had been thin for a while rather
     * than the new orders being unreasonable. Asking for headroom makes each
     * test roll onto a variant that can actually satisfy it, which is what the
     * four sibling classes that hit this first already do.
     */
    /** Units per test order — enough of a ₹120+ variant to clear the ₹299 minimum. */
    private static final int QTY = 5;

    private ProductVariantDto pickPricyVariant() {
        for (ProductDto p : catalogService.listProducts(null, false, null, PageRequest.of(0, 200)).content()) {
            for (ProductVariantDto v : p.variants()) {
                if (v.available() && v.availableStock() >= QTY * 2
                        && v.sellingPrice().compareTo(BigDecimal.valueOf(120)) >= 0) {
                    return v;
                }
            }
        }
        throw new IllegalStateException(
                "No seeded variant with price >= 120 and enough stock left in the shared database");
    }

    @Test
    void failedPaymentRollsBackOrderAndReleasesReservation() {
        when(paymentService.charge(any(), eq(PaymentMethod.UPI), any()))
                .thenReturn(new PaymentResult(PaymentMethod.UPI, PaymentStatus.FAILED, "FAILED-REF"));

        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());

        UUID cartId = cartService.createCart().cartId();
        cartService.addItem(cartId, variant.id(), QTY);
        PlaceOrderRequest req = new PlaceOrderRequest(
                cartId, "Asha Rao", "9999900000",
                new AddressDto("12 MG Road", STORE_LAT, STORE_LNG),
                PaymentMethod.UPI, null, null);

        assertThatThrownBy(() -> orderService.placeOrder(req, "pay-fail-key-1", null))
                .isInstanceOf(BusinessRuleException.class);

        // Reservation rolled back with the order — stock is exactly as before.
        assertThat(inventoryService.availability(variant.id())).isEqualTo(before);
        // The cart is NOT checked out, so the customer can retry / switch to COD.
        assertThat(cartService.getCart(cartId).orElseThrow().checkedOut()).isFalse();
    }
}
