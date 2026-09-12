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
import com.townbasket.payments.PaymentMethod;
import com.townbasket.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/** Admin order search, and the rider's own on/off-duty switch as an assignment gate. */
class OrderSearchAndRiderDutyIntegrationTest extends AbstractIntegrationTest {

    private static final double STORE_LAT = 12.21;
    private static final double STORE_LNG = 76.89;

    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired CatalogService catalogService;
    @Autowired AuthService authService;

    @Test
    void searchMatchesOrderNumberPhoneAndNameWithinAStatus() {
        String phone = "9" + String.valueOf(System.nanoTime()).substring(0, 9); // unique-ish
        OrderDto order = place("Bhavana Q Searchable", phone, "search-" + UUID.randomUUID());

        assertThat(ids(orderService.listOrders(null, String.valueOf(order.id()), PageRequest.of(0, 20))))
                .as("by order number").contains(order.id());
        assertThat(ids(orderService.listOrders(null, phone.substring(3, 8), PageRequest.of(0, 20))))
                .as("by part of the phone").contains(order.id());
        assertThat(ids(orderService.listOrders(null, "q searchABLE", PageRequest.of(0, 20))))
                .as("by part of the name, case-insensitively").contains(order.id());
        assertThat(ids(orderService.listOrders("CONFIRMED", "Bhavana Q", PageRequest.of(0, 20))))
                .as("search within a status").contains(order.id());
        assertThat(ids(orderService.listOrders("DELIVERED", "Bhavana Q", PageRequest.of(0, 20))))
                .as("same search, wrong status").doesNotContain(order.id());
    }

    @Test
    void searchTreatsWildcardsLiterally() {
        place("Percent % Name", "9000000001", "search-pct-" + UUID.randomUUID());
        // A bare "%" would match everything if passed through; escaped, it matches only the one name.
        assertThat(orderService.listOrders(null, "%", PageRequest.of(0, 50)).content())
                .allSatisfy(o -> assertThat(o.customerName()).contains("%"));
    }

    @Test
    void anOffDutyRiderKeepsTheirOrdersButGetsNothingNew() {
        Long rider = authService.createDeliveryAgent(new CreateDeliveryAgentRequest(
                "Duty Rider", "duty-" + UUID.randomUUID().toString().substring(0, 8) + "@townbasket.local",
                "password123")).id();
        assertThat(authService.dutyStatus(rider).onDuty()).as("riders start on duty").isTrue();

        OrderDto held = place("Held Customer", "9000000002", "duty-held-" + UUID.randomUUID());
        orderService.assignAgent(held.id(), rider);

        authService.setDutyStatus(rider, false);
        assertThat(authService.isActiveDeliveryAgent(rider)).as("account still active").isTrue();
        assertThat(authService.isAvailableDeliveryAgent(rider)).isFalse();

        OrderDto fresh = place("Fresh Customer", "9000000003", "duty-fresh-" + UUID.randomUUID());
        assertThatThrownBy(() -> orderService.assignAgent(fresh.id(), rider))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("off duty");
        // The order they already held is untouched and still theirs.
        assertThat(orderService.listAgentOrders(rider, null, PageRequest.of(0, 10)).content())
                .extracting(OrderDto::id).contains(held.id());

        authService.setDutyStatus(rider, true);
        assertThat(orderService.assignAgent(fresh.id(), rider).assignedAgentId()).isEqualTo(rider);
    }

    @Test
    void onlyRidersHaveADutyStatus() {
        Long customer = authService.phoneVerify(new com.townbasket.identity.PhoneVerifyRequest("dev:9777700123")).user().id();
        assertThatThrownBy(() -> authService.setDutyStatus(customer, false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ---- helpers -----------------------------------------------------------

    private static java.util.List<Long> ids(com.townbasket.shared.PagedResponse<OrderDto> page) {
        return page.content().stream().map(OrderDto::id).toList();
    }

    private OrderDto place(String name, String phone, String key) {
        ProductVariantDto v = buyableVariant();
        UUID cartId = cartService.createCart().cartId();
        CartDto cart = cartService.addItem(cartId, v.id(), 5);
        return orderService.placeOrder(new PlaceOrderRequest(cart.cartId(), name, phone,
                new AddressDto("12 MG Road", STORE_LAT, STORE_LNG), PaymentMethod.COD, null, null), key, null);
    }

    private ProductVariantDto buyableVariant() {
        for (ProductDto p : catalogService.listProducts(null, false, null, PageRequest.of(0, 200)).content()) {
            for (ProductVariantDto v : p.variants()) {
                if (v.available() && v.availableStock() >= 20
                        && v.sellingPrice().compareTo(BigDecimal.valueOf(120)) >= 0) {
                    return v;
                }
            }
        }
        throw new IllegalStateException("No seeded variant with price >= 120 and enough stock");
    }
}
