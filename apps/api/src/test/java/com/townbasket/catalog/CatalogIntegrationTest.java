package com.townbasket.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.PagedResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Catalog integration test against a real Postgres (Testcontainers). Exercises
 * Flyway migrations + seed data, the JPA mappings, and the native FTS/trigram
 * search query through the public {@link CatalogService}.
 */
class CatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    CatalogService catalogService;

    @Test
    void listsSeededCategories() {
        List<CategoryDto> categories = catalogService.listCategories();

        assertThat(categories).hasSizeGreaterThanOrEqualTo(8);
        assertThat(categories).extracting(CategoryDto::slug)
                .contains("atta-flours", "rice-dals", "dairy", "cleaning-household");
        // Ordered by sort_order.
        assertThat(categories.get(0).slug()).isEqualTo("atta-flours");
    }

    @Test
    void listsProductsByCategoryWithVariantsButNeverCostPrice() {
        Long dairyId = catalogService.listCategories().stream()
                .filter(c -> c.slug().equals("dairy"))
                .findFirst().orElseThrow().id();

        PagedResponse<ProductDto> page = catalogService.listProducts(dairyId, PageRequest.of(0, 20));

        assertThat(page.totalElements()).isGreaterThanOrEqualTo(5);
        assertThat(page.content()).allSatisfy(p -> {
            assertThat(p.categoryId()).isEqualTo(dairyId);
            assertThat(p.variants()).isNotEmpty();
            // Variant DTO has no cost-price accessor at all (compile-time guarantee);
            // selling price is always present.
            assertThat(p.variants()).allSatisfy(v -> assertThat(v.sellingPrice()).isNotNull());
        });
    }

    @Test
    void findsProductByIdAndBySlug() {
        ProductDto bySlug = catalogService.findProduct("amul-butter").orElseThrow();
        assertThat(bySlug.name()).isEqualTo("Amul Butter");

        ProductDto byId = catalogService.findProduct(String.valueOf(bySlug.id())).orElseThrow();
        assertThat(byId.slug()).isEqualTo("amul-butter");

        assertThat(catalogService.findProduct("no-such-product")).isEmpty();
    }

    @Test
    void searchReturnsExpectedFullTextHits() {
        PagedResponse<ProductDto> results = catalogService.search("atta", PageRequest.of(0, 20));

        assertThat(results.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(results.content()).extracting(ProductDto::slug)
                .contains("aashirvaad-whole-wheat-atta", "fortune-chakki-fresh-atta");
    }

    @Test
    void searchIsTypoTolerantViaTrigram() {
        // "biscit" is a typo for "biscuit" — trigram similarity should still match Parle-G.
        PagedResponse<ProductDto> results = catalogService.search("biscit", PageRequest.of(0, 20));

        assertThat(results.content()).extracting(ProductDto::slug)
                .contains("parle-g-biscuits");
    }
}
