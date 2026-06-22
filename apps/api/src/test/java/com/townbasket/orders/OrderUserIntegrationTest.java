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

    private ProductVariantDto pricyVariant() {
        for (ProductDto p : catalogService.listProducts(null, false, null, PageRequest.of(0, 100)).content()) {
            for (ProductVariantDto v : p.variants()) {
                if (v.available() && v.sellingPrice().compareTo(BigDecimal.valueOf(120)) >= 0) {
                    return v;
                }
            }
        }
        throw new IllegalStateException("No suitable seeded variant found");
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

        OrderDto userOrder = orderService.placeOrder(request(cartWith(v, 5)), "user-order-1", userId);
        OrderDto guestOrder = orderService.placeOrder(request(cartWith(v, 5)), "guest-order-1", null);

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
