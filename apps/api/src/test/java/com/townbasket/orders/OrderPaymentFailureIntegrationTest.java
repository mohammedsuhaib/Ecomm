package com.townbasket.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.cart.CartDto;
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

    @Test
    void failedPaymentRollsBackOrderAndReleasesReservation() {
        when(paymentService.charge(any(), eq(PaymentMethod.UPI), any()))
                .thenReturn(new PaymentResult(1L, PaymentMethod.UPI, PaymentStatus.FAILED, "FAILED-REF"));

        ProductVariantDto variant = pickPricyVariant();
        int before = inventoryService.availability(variant.id());

        UUID cartId = cartService.createCart().cartId();
        cartService.addItem(cartId, variant.id(), 5);
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
