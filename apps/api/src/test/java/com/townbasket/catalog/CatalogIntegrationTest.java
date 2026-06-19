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

        PagedResponse<ProductDto> page = catalogService.listProducts(dairyId, false, null, PageRequest.of(0, 20));

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
        PagedResponse<ProductDto> results = catalogService.search("atta", null, PageRequest.of(0, 20));

        assertThat(results.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(results.content()).extracting(ProductDto::slug)
                .contains("aashirvaad-whole-wheat-atta", "fortune-chakki-fresh-atta");
    }

    @Test
    void searchIsTypoTolerantViaTrigram() {
        // "biscit" is a typo for "biscuit" — trigram similarity should still match Parle-G.
        PagedResponse<ProductDto> results = catalogService.search("biscit", null, PageRequest.of(0, 20));

        assertThat(results.content()).extracting(ProductDto::slug)
                .contains("parle-g-biscuits");
    }

    @Test
    void featuredFilterReturnsOnlyFeaturedProducts() {
        PagedResponse<ProductDto> featured =
                catalogService.listProducts(null, true, null, PageRequest.of(0, 100));

        // V3_3 seed promotes a curated mix (~8 products) across categories.
        assertThat(featured.totalElements()).isBetween(6L, 10L);
        assertThat(featured.content()).isNotEmpty();
        assertThat(featured.content()).allSatisfy(p -> assertThat(p.featured()).isTrue());
        assertThat(featured.content()).extracting(ProductDto::slug)
                .contains("aashirvaad-whole-wheat-atta", "india-gate-basmati-rice", "amul-pure-ghee");

        // Without the filter, the full catalog is larger than the featured subset.
        PagedResponse<ProductDto> all = catalogService.listProducts(null, false, null, PageRequest.of(0, 100));
        assertThat(all.totalElements()).isGreaterThan(featured.totalElements());
    }

    @Test
    void sortByNameOrdersCaseInsensitiveAToZAcrossPages() {
        PagedResponse<ProductDto> page =
                catalogService.listProducts(null, false, ProductSort.NAME, PageRequest.of(0, 100));

        List<String> names = page.content().stream().map(ProductDto::name).toList();
        List<String> expected = names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        assertThat(names).isEqualTo(expected);
    }

    @Test
    void sortByPriceAscOrdersByLowestAvailableVariantPrice() {
        PagedResponse<ProductDto> page =
                catalogService.listProducts(null, false, ProductSort.PRICE_ASC, PageRequest.of(0, 100));

        List<java.math.BigDecimal> lowest = page.content().stream()
                .map(CatalogIntegrationTest::lowestAvailablePrice)
                .toList();
        // Non-decreasing by each product's lowest available variant selling price.
        for (int i = 1; i < lowest.size(); i++) {
            assertThat(lowest.get(i)).isGreaterThanOrEqualTo(lowest.get(i - 1));
        }

        // price_desc is the exact reverse ordering of the same key.
        PagedResponse<ProductDto> desc =
                catalogService.listProducts(null, false, ProductSort.PRICE_DESC, PageRequest.of(0, 100));
        List<java.math.BigDecimal> lowestDesc = desc.content().stream()
                .map(CatalogIntegrationTest::lowestAvailablePrice)
                .toList();
        for (int i = 1; i < lowestDesc.size(); i++) {
            assertThat(lowestDesc.get(i)).isLessThanOrEqualTo(lowestDesc.get(i - 1));
        }
    }

    @Test
    void sortPaginatesOverTheFullSortedSet() {
        // Page boundary must respect the global sort: first item of page 1 (size 5)
        // is the 6th item of the size-10 page.
        PagedResponse<ProductDto> firstTen =
                catalogService.listProducts(null, false, ProductSort.NAME, PageRequest.of(0, 10));
        PagedResponse<ProductDto> secondPageOfFive =
                catalogService.listProducts(null, false, ProductSort.NAME, PageRequest.of(1, 5));

        assertThat(secondPageOfFive.content().get(0).id())
                .isEqualTo(firstTen.content().get(5).id());
    }

    private static java.math.BigDecimal lowestAvailablePrice(ProductDto p) {
        return p.variants().stream()
                .filter(ProductVariantDto::available)
                .map(ProductVariantDto::sellingPrice)
                .min(java.util.Comparator.naturalOrder())
                .orElse(java.math.BigDecimal.valueOf(Long.MAX_VALUE));
    }
}
