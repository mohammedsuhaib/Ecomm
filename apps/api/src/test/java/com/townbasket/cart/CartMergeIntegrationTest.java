package com.townbasket.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.identity.AuthService;
import com.townbasket.identity.PhoneVerifyRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Service-level coverage of cart merge / user-cart semantics (M4_CONTRACT §5)
 * against a real Postgres.
 */
class CartMergeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    CartService cartService;
    @Autowired
    CatalogService catalogService;
    @Autowired
    AuthService authService;

    private Long newUser(String phone) {
        return authService.phoneVerify(new PhoneVerifyRequest("dev:" + phone)).user().id();
    }

    private ProductVariantDto anyVariant() {
        return catalogService.listProducts(null, false, null, PageRequest.of(0, 1)).content().get(0).variants().get(0);
    }

    @Test
    void mergeClaimsGuestCartWhenUserHasNoActiveCart() {
        Long userId = newUser("9777700000");
        ProductVariantDto v = anyVariant();
        UUID guestCart = cartService.createCart().cartId();
        cartService.addItem(guestCart, v.id(), 2);

        CartDto merged = cartService.merge(guestCart, userId);
        // No active user cart -> the guest cart is claimed (same id, now owned).
        assertThat(merged.cartId()).isEqualTo(guestCart);
        assertThat(merged.itemCount()).isEqualTo(2);
    }

    @Test
    void mergeFoldsGuestLinesIntoExistingUserCart() {
        Long userId = newUser("9777700001");
        ProductVariantDto v = anyVariant();

        // User already has an active cart with 1 unit.
        UUID userCart = cartService.createUserCart(userId).cartId();
        cartService.addItem(userCart, v.id(), 1);

        // Guest cart with 3 of the same variant.
        UUID guestCart = cartService.createCart().cartId();
        cartService.addItem(guestCart, v.id(), 3);

        CartDto merged = cartService.merge(guestCart, userId);
        // Returns the USER cart with summed qty; guest cart is checked out.
        assertThat(merged.cartId()).isEqualTo(userCart);
        assertThat(merged.itemCount()).isEqualTo(4);
        assertThat(cartService.getCart(guestCart)).isPresent(); // still exists, but checked out
    }

    @Test
    void mergeWithStaleGuestIdReturnsUserCartAndNeverFails() {
        Long userId = newUser("9777700002");
        CartDto merged = cartService.merge(UUID.randomUUID(), userId);
        assertThat(merged).isNotNull();
        assertThat(merged.items()).isEmpty();
    }
}
