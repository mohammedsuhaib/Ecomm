package com.townbasket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Physical-count corrections against a real Postgres.
 *
 * <p>The reserved-units floor is the rule staff actually run into ("unable to
 * update stock quantity"), so it is pinned here along with the wording of the
 * refusal: the admin UI shows these messages verbatim, and a message that only
 * says "failed" leaves an operator retrying a value that can never be accepted.
 */
class StockCorrectionIntegrationTest extends AbstractIntegrationTest {

    private static final Long STORE = 1L;

    @Autowired AdminInventoryService adminInventory;
    @Autowired InventoryService inventory;

    /** A variant with stock at the seeded store, and its current level. */
    private StockLevelDto anyStocked() {
        return adminInventory.listStockLevels(STORE, null, 0, 100).content().stream()
                .filter(s -> s.reserved() == 0 && s.onHand() >= 20)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No unreserved seeded stock to correct"));
    }

    private StockLevelDto reload(Long variantId) {
        return adminInventory.listStockLevels(STORE, null, 0, 200).content().stream()
                .filter(s -> s.variantId().equals(variantId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aCountIsSetToTheAbsoluteValueGiven() {
        StockLevelDto before = anyStocked();

        adminInventory.correctStock(STORE, before.variantId(), 7, "physical count");

        StockLevelDto after = reload(before.variantId());
        assertThat(after.onHand()).isEqualTo(7);
        // available = on_hand - reserved, and nothing was reserved here.
        assertThat(after.available()).isEqualTo(7);
    }

    @Test
    void aCountBelowTheReservedUnitsIsRefusedAndChangesNothing() {
        StockLevelDto stocked = anyStocked();
        Long variantId = stocked.variantId();
        // Reserve 4 units: they are physically committed to an open order, so the
        // shelf count can never be corrected below them.
        inventory.reserve(STORE, 90_001L, List.of(new ReservationLine(variantId, 4)));
        int onHandBefore = reload(variantId).onHand();

        assertThatThrownBy(() -> adminInventory.correctStock(STORE, variantId, 2, "physical count"))
                .isInstanceOf(BusinessRuleException.class)
                // Staff read this verbatim in the admin UI: it has to name the
                // blocker AND the way out, and must not leak a request field name.
                .hasMessageContaining("4 unit(s) are already reserved")
                .hasMessageContaining("Cancel or fulfil those orders")
                .hasMessageNotContaining("newOnHand");

        assertThat(reload(variantId).onHand())
                .as("a refused correction must not have moved the count")
                .isEqualTo(onHandBefore);

        // Exactly the reserved amount is allowed — that is the floor, not a gap.
        adminInventory.correctStock(STORE, variantId, 4, "physical count");
        StockLevelDto after = reload(variantId);
        assertThat(after.onHand()).isEqualTo(4);
        assertThat(after.available()).isZero();

        inventory.releaseReservation(90_001L);
    }

    @Test
    void aNegativeCountIsRefusedInPlainWords() {
        StockLevelDto stocked = anyStocked();

        assertThatThrownBy(() -> adminInventory.correctStock(STORE, stocked.variantId(), -1, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can't be negative")
                .hasMessageNotContaining("newOnHand");
    }

    @Test
    void correctingAVariantWithNoStockRowIsNotFound() {
        assertThatThrownBy(() -> adminInventory.correctStock(STORE, 9_999_999L, 5, "physical count"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anUnchangedCountIsAcceptedWithoutComplaint() {
        StockLevelDto stocked = anyStocked();
        int onHand = stocked.onHand();

        // Re-entering the same number is a no-op, not an error — staff confirming
        // a count that already matches must not see a failure.
        adminInventory.correctStock(STORE, stocked.variantId(), onHand, "physical count");

        assertThat(reload(stocked.variantId()).onHand()).isEqualTo(onHand);
    }
}
