package com.townbasket.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.PagedResponse;
import com.townbasket.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Admin catalogue management integration test against a real Postgres
 * (Testcontainers). Exercises the full write lifecycle through the public
 * {@link CatalogService} admin surface: create category -> create product with
 * variants -> read back (with cost price + Kannada name) -> update -> toggle
 * availability -> variant CRUD -> guarded category delete (422) -> product delete.
 *
 * <p>Mirrors {@link CatalogIntegrationTest}'s service-level style. The HTTP-level
 * security matrix for {@code /api/v1/admin/catalog/**} is asserted separately in
 * {@code SecurityIntegrationTest} (which boots a live port).
 */
class AdminCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    CatalogService catalogService;

    @Test
    void fullCategoryAndProductLifecycle() {
        // --- create category (slug auto-generated from name) ---
        CategoryDto category = catalogService.createCategory(
                new CreateCategoryRequest("Test Snacks & Treats", null, null, "http://img/cat.png"));
        assertThat(category.id()).isNotNull();
        assertThat(category.slug()).isEqualTo("test-snacks-treats");
        assertThat(category.imageUrl()).isEqualTo("http://img/cat.png");
        // Auto sort order is max+10 (>0 because seed categories exist).
        assertThat(category.sortOrder()).isGreaterThan(0);

        // Appears in the admin category list.
        assertThat(catalogService.adminListCategories())
                .extracting(CategoryDto::slug)
                .contains("test-snacks-treats");

        // --- create product WITH variants ---
        AdminProductDto product = catalogService.createProduct(new CreateProductRequest(
                "Crunchy Wafers",
                "ಕ್ರಂಚಿ",          // explicit nameKn supplied -> kept verbatim
                null,                 // slug auto
                category.id(),
                "A tasty test product",
                null,                 // vegMarker -> default true
                "http://img/p.png",
                null,                 // available -> default true
                Boolean.TRUE,         // featured
                List.of(
                        new CreateVariantRequest("100 g",
                                new BigDecimal("20.00"), new BigDecimal("12.00"),
                                new BigDecimal("25.00"), null, 1),
                        new CreateVariantRequest("200 g",
                                new BigDecimal("38.00"), new BigDecimal("22.00"),
                                null, Boolean.FALSE, 2))));

        assertThat(product.id()).isNotNull();
        assertThat(product.slug()).isEqualTo("crunchy-wafers");
        assertThat(product.categoryId()).isEqualTo(category.id());
        assertThat(product.categoryName()).isEqualTo("Test Snacks & Treats");
        assertThat(product.nameKn()).isEqualTo("ಕ್ರಂಚಿ");
        assertThat(product.vegMarker()).isTrue();
        assertThat(product.available()).isTrue();
        assertThat(product.featured()).isTrue();
        assertThat(product.variants()).hasSize(2);

        // --- GET returns it WITH cost price (admin-only field) + nameKn ---
        AdminProductDto fetched = catalogService.adminGetProduct(product.id());
        assertThat(fetched.nameKn()).isEqualTo("ಕ್ರಂಚಿ");
        AdminVariantDto firstVariant = fetched.variants().get(0);
        assertThat(firstVariant.label()).isEqualTo("100 g");
        assertThat(firstVariant.sellingPrice()).isEqualByComparingTo("20.00");
        assertThat(firstVariant.costPrice()).isEqualByComparingTo("12.00");
        assertThat(firstVariant.mrp()).isEqualByComparingTo("25.00");
        assertThat(firstVariant.available()).isTrue();
        assertThat(fetched.variants().get(1).available()).isFalse();

        // Admin product list INCLUDES this product, scoped by category.
        PagedResponse<AdminProductDto> listed =
                catalogService.adminListProducts(category.id(), null, PageRequest.of(0, 50));
        assertThat(listed.content()).extracting(AdminProductDto::slug).contains("crunchy-wafers");

        // Name search finds it.
        PagedResponse<AdminProductDto> searched =
                catalogService.adminListProducts(null, "crunchy", PageRequest.of(0, 50));
        assertThat(searched.content()).extracting(AdminProductDto::slug).contains("crunchy-wafers");

        // --- update product (slug immutable) ---
        AdminProductDto updated = catalogService.updateProduct(product.id(), new UpdateProductRequest(
                "Crunchy Wafers Deluxe",
                null,                 // nameKn null + name changed -> best-effort re-transliterate (no-op in tests)
                category.id(),
                "Updated description",
                Boolean.FALSE,        // vegMarker -> false
                null,
                null,
                Boolean.FALSE));      // featured -> false
        assertThat(updated.name()).isEqualTo("Crunchy Wafers Deluxe");
        assertThat(updated.slug()).isEqualTo("crunchy-wafers"); // immutable
        assertThat(updated.vegMarker()).isFalse();
        assertThat(updated.featured()).isFalse();
        assertThat(updated.description()).isEqualTo("Updated description");

        // --- toggle product availability ---
        assertThat(catalogService.setProductAvailability(product.id(), false).available()).isFalse();
        assertThat(catalogService.setProductAvailability(product.id(), true).available()).isTrue();

        // --- variant CRUD ---
        AdminVariantDto added = catalogService.addVariant(product.id(),
                new CreateVariantRequest("500 g",
                        new BigDecimal("80.00"), new BigDecimal("50.00"),
                        new BigDecimal("90.00"), null, 3));
        assertThat(added.id()).isNotNull();
        assertThat(added.costPrice()).isEqualByComparingTo("50.00");
        assertThat(catalogService.adminGetProduct(product.id()).variants()).hasSize(3);

        AdminVariantDto editedVariant = catalogService.updateVariant(product.id(), added.id(),
                new UpdateVariantRequest("500 g pack",
                        new BigDecimal("78.00"), new BigDecimal("48.00"),
                        new BigDecimal("88.00"), Boolean.FALSE, 5));
        assertThat(editedVariant.label()).isEqualTo("500 g pack");
        assertThat(editedVariant.sellingPrice()).isEqualByComparingTo("78.00");
        assertThat(editedVariant.available()).isFalse();
        assertThat(editedVariant.sortOrder()).isEqualTo(5);

        assertThat(catalogService.setVariantAvailability(product.id(), added.id(), true).available()).isTrue();

        catalogService.deleteVariant(product.id(), added.id());
        assertThat(catalogService.adminGetProduct(product.id()).variants()).hasSize(2);

        // --- deleting a category that still has products -> 422 (BusinessRuleException) ---
        assertThatThrownBy(() -> catalogService.deleteCategory(category.id()))
                .isInstanceOf(BusinessRuleException.class);

        // --- delete the product (hard delete, cascades variants) ---
        catalogService.deleteProduct(product.id());
        assertThatThrownBy(() -> catalogService.adminGetProduct(product.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        // --- now the category can be deleted ---
        catalogService.deleteCategory(category.id());
        assertThat(catalogService.adminListCategories())
                .extracting(CategoryDto::slug)
                .doesNotContain("test-snacks-treats");
    }

    @Test
    void slugCollisionsAreSuffixed() {
        CategoryDto first = catalogService.createCategory(
                new CreateCategoryRequest("Dupe Cat", null, null, null));
        CategoryDto second = catalogService.createCategory(
                new CreateCategoryRequest("Dupe Cat", null, null, null));
        assertThat(first.slug()).isEqualTo("dupe-cat");
        assertThat(second.slug()).isEqualTo("dupe-cat-2");

        // cleanup
        catalogService.deleteCategory(first.id());
        catalogService.deleteCategory(second.id());
    }

    @Test
    void validationRejectsBadInput() {
        // Blank name -> 422.
        assertThatThrownBy(() -> catalogService.createCategory(
                new CreateCategoryRequest("  ", null, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        // Unknown category -> 404.
        assertThatThrownBy(() -> catalogService.createProduct(new CreateProductRequest(
                "Orphan", null, null, 999_999L, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        CategoryDto cat = catalogService.createCategory(
                new CreateCategoryRequest("Valid Cat", null, null, null));
        // Negative selling price -> 422.
        assertThatThrownBy(() -> catalogService.createProduct(new CreateProductRequest(
                "Bad Variant", null, null, cat.id(), null, null, null, null, null,
                List.of(new CreateVariantRequest("x",
                        new BigDecimal("-1.00"), new BigDecimal("0.00"), null, null, null)))))
                .isInstanceOf(BusinessRuleException.class);

        catalogService.deleteCategory(cat.id());
    }

    @Test
    void rejectsMrpBelowSellingPrice() {
        CategoryDto cat = catalogService.createCategory(
                new CreateCategoryRequest("Pricing Cat", null, null, null));

        // MRP (40) below selling price (50) is a data error (negative discount /
        // broken strikethrough) -> 422 on create.
        assertThatThrownBy(() -> catalogService.createProduct(new CreateProductRequest(
                "Mispriced", null, null, cat.id(), null, null, null, null, null,
                List.of(new CreateVariantRequest("1 kg",
                        new BigDecimal("50.00"), new BigDecimal("30.00"),
                        new BigDecimal("40.00"), null, null)))))
                .isInstanceOf(BusinessRuleException.class);

        // MRP == selling price is allowed (no discount shown).
        AdminProductDto ok = catalogService.createProduct(new CreateProductRequest(
                "Priced Right", null, null, cat.id(), null, null, null, null, null,
                List.of(new CreateVariantRequest("1 kg",
                        new BigDecimal("50.00"), new BigDecimal("30.00"),
                        new BigDecimal("50.00"), null, null))));
        assertThat(ok.variants()).hasSize(1);

        // ...and updateVariant enforces the same rule (MRP 10 < selling 50) -> 422.
        Long variantId = ok.variants().get(0).id();
        assertThatThrownBy(() -> catalogService.updateVariant(ok.id(), variantId,
                new UpdateVariantRequest("1 kg",
                        new BigDecimal("50.00"), new BigDecimal("30.00"),
                        new BigDecimal("10.00"), null, null)))
                .isInstanceOf(BusinessRuleException.class);

        // cleanup
        catalogService.deleteProduct(ok.id());
        catalogService.deleteCategory(cat.id());
    }
}
