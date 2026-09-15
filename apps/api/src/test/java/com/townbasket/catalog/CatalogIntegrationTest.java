package com.townbasket.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.PagedResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Catalog integration test against a real Postgres (Testcontainers). Exercises
 * Flyway migrations + seed data, the JPA mappings, and the native FTS/trigram
 * search query through the public {@link CatalogService}.
 */
class CatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    CatalogService catalogService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void productCarriesStoredKannadaNameAndNullByDefault() {
        // name_kn is nullable and unset by the seed (the backfill job is disabled
        // in tests), so it surfaces as null until populated.
        assertThat(catalogService.findProduct("amul-paneer").orElseThrow().nameKn()).isNull();

        jdbc.update("UPDATE catalog.products SET name_kn = ? WHERE slug = ?",
                "ಅಮುಲ್ ಬಟರ್", "amul-butter");

        ProductDto butter = catalogService.findProduct("amul-butter").orElseThrow();
        assertThat(butter.name()).isEqualTo("Amul Butter");
        assertThat(butter.nameKn()).isEqualTo("ಅಮುಲ್ ಬಟರ್");
    }

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
    void searchFindsAProductByItsKannadaName() {
        // The storefront shows name_kn to customers browsing in Kannada, so a
        // Kannada query has to find it. Before catalog V3_9 the search vector
        // carried only the English name and this returned nothing at all.
        //
        // The UPDATE also exercises the second half of that fix: the trigger now
        // fires on a change to name_kn ALONE (which is all the transliteration
        // backfill ever writes), so the vector is rebuilt here without any other
        // column being touched.
        jdbc.update("UPDATE catalog.products SET name_kn = ? WHERE slug = ?",
                "ಟಾಟಾ ಚಹಾ ಪ್ರೀಮಿಯಂ", "tata-tea-premium");

        PagedResponse<ProductDto> byFullKannadaName =
                catalogService.search("ಟಾಟಾ ಚಹಾ ಪ್ರೀಮಿಯಂ", null, PageRequest.of(0, 20));
        assertThat(byFullKannadaName.content()).extracting(ProductDto::slug)
                .as("the whole Kannada name").contains("tata-tea-premium");

        PagedResponse<ProductDto> byOneKannadaWord =
                catalogService.search("ಚಹಾ", null, PageRequest.of(0, 20));
        assertThat(byOneKannadaWord.content()).extracting(ProductDto::slug)
                .as("one word of it — full-text, not a whole-string match")
                .contains("tata-tea-premium");

        // The English name still finds it: Kannada is additive, not a swap.
        assertThat(catalogService.search("Tata Tea", null, PageRequest.of(0, 20)).content())
                .extracting(ProductDto::slug).contains("tata-tea-premium");
    }

    @Test
    void kannadaSearchSurvivesSorting() {
        // The sorted path is a separate query whose match set must stay identical
        // to the unsorted one — re-sorting may change the order, never the hits.
        jdbc.update("UPDATE catalog.products SET name_kn = ? WHERE slug = ?",
                "ಅಮುಲ್ ಹಾಲು", "amul-gold-milk");

        for (ProductSort sort : new ProductSort[] {ProductSort.PRICE_ASC, ProductSort.NAME}) {
            assertThat(catalogService.search("ಅಮುಲ್ ಹಾಲು", sort, PageRequest.of(0, 20)).content())
                    .as("sorted by %s", sort)
                    .extracting(ProductDto::slug).contains("amul-gold-milk");
        }
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
    void sortByNameDescOrdersZToAAndMirrorsAToZ() {
        PagedResponse<ProductDto> desc =
                catalogService.listProducts(null, false, ProductSort.NAME_DESC, PageRequest.of(0, 100));

        List<String> names = desc.content().stream().map(ProductDto::name).toList();
        List<String> expected = names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER.reversed())
                .toList();
        assertThat(names).isEqualTo(expected);

        // Both directions must cover exactly the same products — a reversed sort
        // that quietly drops or duplicates one would still look "descending".
        // Compared as a collection rather than a reversed list on purpose: two
        // products sharing a name tie-break on id ASCENDING in both directions,
        // so the lists are not strict mirrors of each other.
        PagedResponse<ProductDto> asc =
                catalogService.listProducts(null, false, ProductSort.NAME, PageRequest.of(0, 100));
        assertThat(desc.content().stream().map(ProductDto::id).toList())
                .containsExactlyInAnyOrderElementsOf(
                        asc.content().stream().map(ProductDto::id).toList());
        assertThat(desc.totalElements()).isEqualTo(asc.totalElements());
    }

    @Test
    void theSortQueryValueAcceptsBothNameSpellings() {
        // name_desc is the new option; name_asc is a synonym for the original
        // name, so the pair reads symmetrically without breaking old links.
        assertThat(ProductSort.parse("name_desc")).contains(ProductSort.NAME_DESC);
        assertThat(ProductSort.parse("NAME_DESC")).contains(ProductSort.NAME_DESC);
        assertThat(ProductSort.parse("name")).contains(ProductSort.NAME);
        assertThat(ProductSort.parse("name_asc")).contains(ProductSort.NAME);
        // Anything unknown falls back to the endpoint's default order.
        assertThat(ProductSort.parse("z_to_a")).isEmpty();
        assertThat(ProductSort.parse(null)).isEmpty();
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

    @Test
    void aProductMarkedUnavailableMakesEveryVariantUnavailable() {
        // The bug this pins: products and variants carry independent `available`
        // switches and the product's does NOT cascade, so every reader has to
        // apply it. None did — a product the store had turned off still reported
        // available variants, which is what let an unavailable item be ordered.
        ProductDto before = catalogService.findProduct("amul-butter").orElseThrow();
        assertThat(before.variants()).isNotEmpty();
        assertThat(before.variants()).anySatisfy(v -> assertThat(v.available()).isTrue());

        jdbc.update("UPDATE catalog.products SET available = FALSE WHERE slug = ?", "amul-butter");
        try {
            ProductDto off = catalogService.findProduct("amul-butter").orElseThrow();
            assertThat(off.available()).isFalse();
            // Every variant, regardless of its own flag, and with no sellable stock.
            assertThat(off.variants()).allSatisfy(v -> {
                assertThat(v.available()).isFalse();
                assertThat(v.availableStock()).isZero();
            });

            // And the cross-module view that cart/orders read agrees — this is the
            // one that decides whether checkout accepts the line.
            List<Long> variantIds = off.variants().stream().map(ProductVariantDto::id).toList();
            assertThat(catalogService.findVariants(variantIds).values())
                    .isNotEmpty()
                    .allSatisfy(v -> assertThat(v.available()).isFalse());
        } finally {
            jdbc.update("UPDATE catalog.products SET available = TRUE WHERE slug = ?", "amul-butter");
        }
    }
}
