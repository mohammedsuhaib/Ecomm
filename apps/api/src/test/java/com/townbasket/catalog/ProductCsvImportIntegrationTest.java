package com.townbasket.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.catalog.internal.ProductCsvImporter;
import com.townbasket.inventory.AdminInventoryService;
import com.townbasket.inventory.StockLevelDto;
import com.townbasket.shared.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * CSV bulk-import integration test against a real Postgres (Testcontainers):
 * multi-variant grouping, re-run idempotency (skip existing), per-row errors
 * with partial success, dry-run leaving no writes, and header validation.
 * Uses the seeded 'dairy' category.
 */
class ProductCsvImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ProductCsvImporter importer;
    @Autowired
    CatalogService catalogService;
    @Autowired
    AdminInventoryService adminInventoryService;

    private static final String HEADER =
            "name,category,variant_label,selling_price,cost_price,mrp,veg,hsn,gst_rate\n";

    @Test
    void importsGroupsSkipsAndReportsErrors() {
        String csv = HEADER
                // Two variants of one product (grouped by name), quoted comma in name.
                + "\"Import Dahi, Cup\",dairy,200 g,25,20,28,Y,0403,5\n"
                + "\"Import Dahi, Cup\",dairy,400 g,45,38,50,Y,0403,5\n"
                // Single-variant product.
                + "Import Ghee,dairy,500 ml,320,290,340,Y,0405,5\n"
                // Bad: unknown category.
                + "Import Broken,nope,1 kg,10,8,,Y,,0\n"
                // Bad: illegal GST slab.
                + "Import BadRate,dairy,1 kg,10,8,,Y,,12\n";

        ProductImportResult result = importer.importCsv(csv, false);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("Unknown category"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("GST rate"));

        // The grouped product landed with both variants, in row order.
        AdminProductDto dahi = catalogService
                .adminListProducts(null, "Import Dahi", PageRequest.of(0, 10))
                .content().get(0);
        assertThat(dahi.variants()).hasSize(2);
        assertThat(dahi.variants().get(0).label()).isEqualTo("200 g");
        assertThat(dahi.gstRatePercent()).isEqualByComparingTo("5");
        assertThat(dahi.hsnCode()).isEqualTo("0403");

        // The VariantCreated events (async, after commit) open zero-stock rows,
        // so the imported variants appear in the admin stock list immediately.
        Long variantId = dahi.variants().get(0).id();
        eventually(() -> assertThat(
                adminInventoryService.listStockLevels(1L, null, 0, 500).content())
                .anySatisfy(s -> {
                    assertThat(s.variantId()).isEqualTo(variantId);
                    assertThat(s.onHand()).isZero();
                }));

        // Re-running the same file skips everything that already exists.
        ProductImportResult rerun = importer.importCsv(csv, false);
        assertThat(rerun.created()).isZero();
        assertThat(rerun.skipped()).isEqualTo(2);

        // Dry-run of a new product validates but writes nothing.
        String dry = HEADER + "Import DryRun Only,dairy,1 kg,99,80,,Y,,5\n";
        ProductImportResult dryRun = importer.importCsv(dry, true);
        assertThat(dryRun.created()).isEqualTo(1);
        assertThat(catalogService.adminListProducts(null, "Import DryRun", PageRequest.of(0, 10))
                .content()).isEmpty();
    }

    @Test
    void rejectsMissingRequiredColumns() {
        assertThatThrownBy(() -> importer.importCsv("name,category\nX,dairy\n", false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Missing required column");
    }
}
