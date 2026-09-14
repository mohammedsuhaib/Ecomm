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
import com.townbasket.identity.PhoneVerifyRequest;
import com.townbasket.payments.PaymentMethod;
import com.townbasket.shared.PagedResponse;
import com.townbasket.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Service-level coverage of order tie-to-user, history and reorder
 * (M4_CONTRACT §4) against a real Postgres.
 */
class OrderUserIntegrationTest extends AbstractIntegrationTest {

    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;

    @Autowired
    OrderService orderService;
    @Autowired
    CartService cartService;
    @Autowired
    CatalogService catalogService;
    @Autowired
    AuthService authService;

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

    private ProductVariantDto pricyVariant() {
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

    private PlaceOrderRequest request(UUID cartId) {
        return new PlaceOrderRequest(cartId, "Asha Rao", "9999900000",
                new AddressDto("12 MG Road", STORE_LAT, STORE_LNG), PaymentMethod.COD, null, null);
    }

    private UUID cartWith(ProductVariantDto v, int qty) {
        UUID cartId = cartService.createCart().cartId();
        cartService.addItem(cartId, v.id(), qty);
        return cartId;
    }

    @Test
    void orderTiesToUserWhenPresentAndAppearsInHistory() {
        Long userId = authService.phoneVerify(new PhoneVerifyRequest("dev:9666600000")).user().id();
        ProductVariantDto v = pricyVariant();

        OrderDto userOrder = orderService.placeOrder(request(cartWith(v, QTY)), "user-order-1", userId);
        OrderDto guestOrder = orderService.placeOrder(request(cartWith(v, QTY)), "guest-order-1", null);

        PagedResponse<OrderDto> mine = orderService.listUserOrders(userId, PageRequest.of(0, 20));
        assertThat(mine.content()).extracting(OrderDto::id).contains(userOrder.id());
        assertThat(mine.content()).extracting(OrderDto::id).doesNotContain(guestOrder.id());
    }

    @Test
    void reorderCreatesUserCartFromAvailableLines() {
        Long userId = authService.phoneVerify(new PhoneVerifyRequest("dev:9666600001")).user().id();
        ProductVariantDto v = pricyVariant();

        OrderDto order = orderService.placeOrder(request(cartWith(v, 3)), "reorder-1", userId);

        CartDto cart = orderService.reorder(order.id(), userId);
        assertThat(cart.cartId()).isNotNull();
        assertThat(cart.items()).extracting("variantId").contains(v.id());
        assertThat(cart.itemCount()).isEqualTo(3);
    }

    @Test
    void reorderRejectsForeignOrder() {
        Long owner = authService.phoneVerify(new PhoneVerifyRequest("dev:9666600002")).user().id();
        Long other = authService.phoneVerify(new PhoneVerifyRequest("dev:9666600003")).user().id();
        ProductVariantDto v = pricyVariant();
        OrderDto order = orderService.placeOrder(request(cartWith(v, 3)), "reorder-foreign-1", owner);

        assertThatThrownBy(() -> orderService.reorder(order.id(), other))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orderService.reorder(999_999L, owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
