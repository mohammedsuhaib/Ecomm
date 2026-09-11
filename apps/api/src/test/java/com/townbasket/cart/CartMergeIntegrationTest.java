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

    /**
     * The cross-device flow that used to lose the basket: a cart created WHILE
     * LOGGED IN was anonymous (only the login-time merge ever set user_id), so a
     * second device — with no cartId in ITS localStorage — had no way to find
     * it. Ownership at creation + activeCartFor() is the fix.
     */
    @Test
    void cartCreatedWhileLoggedInIsFoundFromAnotherDevice() {
        Long userId = newUser("9777700010");
        ProductVariantDto v = anyVariant();

        // Device 1: logged-in customer's first add creates the cart.
        UUID phoneCart = cartService.createCart(userId).cartId();
        cartService.addItem(phoneCart, v.id(), 2);

        // Device 2: fresh browser, no local cartId — the login flow asks the server.
        CartDto found = cartService.activeCartFor(userId).orElseThrow();
        assertThat(found.cartId()).isEqualTo(phoneCart);
        assertThat(found.itemCount()).isEqualTo(2);
    }

    @Test
    void activeCartPrefersTheMostRecentlyTouchedOpenCart() {
        Long userId = newUser("9777700011");
        ProductVariantDto v = anyVariant();

        UUID older = cartService.createCart(userId).cartId();
        UUID newer = cartService.createCart(userId).cartId();
        // Touch the older one LAST — recency is by activity, not creation order,
        // so the basket the customer actually used wins.
        cartService.addItem(newer, v.id(), 1);
        cartService.addItem(older, v.id(), 5);

        assertThat(cartService.activeCartFor(userId).orElseThrow().cartId()).isEqualTo(older);
    }

    @Test
    void activeCartIgnoresCheckedOutCartsAndOtherUsers() {
        Long userId = newUser("9777700012");
        Long otherUser = newUser("9777700013");
        ProductVariantDto v = anyVariant();

        UUID ordered = cartService.createCart(userId).cartId();
        cartService.addItem(ordered, v.id(), 1);
        cartService.markCheckedOut(ordered);
        cartService.createCart(otherUser);

        // An ordered cart is history, and another user's cart is not "mine".
        assertThat(cartService.activeCartFor(userId)).isEmpty();
    }

    @Test
    void guestCreatedCartStaysUnowned() {
        Long userId = newUser("9777700014");
        cartService.createCart((Long) null);

        // A guest cart must never surface as anyone's cart until merge claims it.
        assertThat(cartService.activeCartFor(userId)).isEmpty();
    }
}
