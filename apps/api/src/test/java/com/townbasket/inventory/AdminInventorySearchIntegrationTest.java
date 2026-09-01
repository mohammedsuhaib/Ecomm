package com.townbasket.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.PagedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Stock-level search and paging.
 *
 * <p>Regression guard: the admin panel used to filter the CURRENT PAGE in the
 * browser while the pager described the whole store, so a search only ever found
 * matches that happened to be on the page you were looking at, and the page
 * count belonged to a different result set. The filter has to be applied in SQL
 * — to the COUNT as well as the rows — for either to be right.
 */
class AdminInventorySearchIntegrationTest extends AbstractIntegrationTest {

    private static final Long STORE = 1L;

    @Autowired
    AdminInventoryService adminInventoryService;

    @Test
    void searchSpansTheWholeStoreNotJustTheRequestedPage() {
        // Page 1 deliberately holds a single row, so anything the search finds
        // beyond it proves the filter is not page-local.
        PagedResponse<StockLevelDto> firstPage = adminInventoryService.listStockLevels(STORE, null, 0, 1);
        assertThat(firstPage.content()).hasSize(1);
        assertThat(firstPage.totalElements()).isGreaterThan(1);

        // Take a product that is NOT on that first page and search for it.
        PagedResponse<StockLevelDto> everything =
                adminInventoryService.listStockLevels(STORE, null, 0, 500);
        String onFirstPage = firstPage.content().get(0).productName();
        String elsewhere = everything.content().stream()
                .map(StockLevelDto::productName)
                .filter(name -> !name.equals(onFirstPage))
                .findFirst()
                .orElseThrow();

        PagedResponse<StockLevelDto> hits = adminInventoryService.listStockLevels(STORE, elsewhere, 0, 1);
        assertThat(hits.totalElements()).isGreaterThan(0);
        assertThat(hits.content()).isNotEmpty();
        assertThat(hits.content().get(0).productName()).isEqualTo(elsewhere);
    }

    @Test
    void theTotalDescribesTheFilteredSetSoThePagerIsHonest() {
        PagedResponse<StockLevelDto> all = adminInventoryService.listStockLevels(STORE, null, 0, 500);
        String term = all.content().get(0).productName();

        PagedResponse<StockLevelDto> filtered = adminInventoryService.listStockLevels(STORE, term, 0, 500);

        assertThat(filtered.totalElements())
                .isLessThan(all.totalElements())
                .isEqualTo(filtered.content().size());
        assertThat(filtered.content()).allSatisfy(row ->
                assertThat(row.productName().toLowerCase()).contains(term.toLowerCase()));
    }

    @Test
    void searchMatchesVariantLabelAndIgnoresCase() {
        StockLevelDto sample = adminInventoryService.listStockLevels(STORE, null, 0, 1).content().get(0);

        assertThat(adminInventoryService.listStockLevels(STORE, sample.variantLabel(), 0, 50).content())
                .as("variant label is searchable, not just the product name")
                .isNotEmpty();
        assertThat(adminInventoryService.listStockLevels(STORE, sample.productName().toUpperCase(), 0, 50)
                .totalElements())
                .isEqualTo(adminInventoryService.listStockLevels(STORE, sample.productName().toLowerCase(), 0, 50)
                        .totalElements());
    }

    @Test
    void blankSearchListsEverythingAndWildcardsAreLiteral() {
        long all = adminInventoryService.listStockLevels(STORE, null, 0, 1).totalElements();

        assertThat(adminInventoryService.listStockLevels(STORE, "   ", 0, 1).totalElements()).isEqualTo(all);
        assertThat(adminInventoryService.listStockLevels(STORE, "", 0, 1).totalElements()).isEqualTo(all);

        // A bare "%" must not behave as "match everything" — it is a character
        // the user typed, not a pattern.
        assertThat(adminInventoryService.listStockLevels(STORE, "%", 0, 1).totalElements())
                .as("LIKE wildcards in user input are escaped")
                .isLessThan(all);
    }

    @Test
    void pagingThroughMatchesIsStableAndNonOverlapping() {
        // Walk a filtered result set one page at a time; every row should be seen
        // exactly once (the old code re-filtered whole unfiltered pages instead).
        PagedResponse<StockLevelDto> all = adminInventoryService.listStockLevels(STORE, null, 0, 500);
        String term = all.content().get(0).variantLabel();

        long total = adminInventoryService.listStockLevels(STORE, term, 0, 1).totalElements();
        var seen = new java.util.HashSet<Long>();
        for (int page = 0; page * 2 < total; page++) {
            for (StockLevelDto row : adminInventoryService.listStockLevels(STORE, term, page, 2).content()) {
                assertThat(seen.add(row.variantId()))
                        .as("variant %s appeared on more than one page", row.variantId())
                        .isTrue();
            }
        }
        assertThat(seen).hasSize((int) total);
    }
}
