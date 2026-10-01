package com.townbasket.catalog.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ProductNameBackfillJobTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductNameTransliterator transliterator = mock(ProductNameTransliterator.class);
    private final ProductNameWriter writer = mock(ProductNameWriter.class);
    private final ProductNameBackfillJob job = new ProductNameBackfillJob(
            products, categories, transliterator, writer,
            new TransliterationProperties("https://example.invalid", "kn-t-i0-und", 25));

    @Test
    void fillsCategoriesThatHaveNoKannadaName() {
        CategoryEntity rice = category(7L, "Rice & Grains");
        when(products.findByNameKnIsNull(any(Pageable.class))).thenReturn(List.of());
        when(categories.findByNameKnIsNull(any(Pageable.class))).thenReturn(List.of(rice));
        when(transliterator.toKannada("Rice & Grains")).thenReturn(Optional.of("ರೈಸ್ & ಗ್ರೇನ್ಸ್"));

        job.backfillMissingNames();

        verify(writer).saveCategory(7L, "ರೈಸ್ & ಗ್ರೇನ್ಸ್");
        verify(writer, never()).save(anyLong(), anyString());
    }

    @Test
    void fillsProductsAndCategoriesInTheSameSweep() {
        ProductEntity salt = mock(ProductEntity.class);
        when(salt.getId()).thenReturn(3L);
        when(salt.getName()).thenReturn("Tata Salt");
        CategoryEntity snacks = category(9L, "Snacks");
        when(products.findByNameKnIsNull(any(Pageable.class))).thenReturn(List.of(salt));
        when(categories.findByNameKnIsNull(any(Pageable.class))).thenReturn(List.of(snacks));
        when(transliterator.toKannada(anyString())).thenReturn(Optional.of("ಕನ್ನಡ"));

        job.backfillMissingNames();

        verify(writer).save(3L, "ಕನ್ನಡ");
        verify(writer).saveCategory(9L, "ಕನ್ನಡ");
    }

    @Test
    void backsOffWhenNothingCanBeFilled() {
        CategoryEntity snacks = category(9L, "Snacks");
        when(products.findByNameKnIsNull(any(Pageable.class))).thenReturn(List.of());
        when(categories.findByNameKnIsNull(any(Pageable.class))).thenReturn(List.of(snacks));
        when(transliterator.toKannada(anyString())).thenReturn(Optional.empty());

        job.backfillMissingNames(); // fails, schedules one skipped sweep
        job.backfillMissingNames(); // skipped

        verify(transliterator, times(1)).toKannada("Snacks");
        verify(writer, never()).saveCategory(anyLong(), anyString());
    }

    private static CategoryEntity category(Long id, String name) {
        CategoryEntity c = mock(CategoryEntity.class);
        when(c.getId()).thenReturn(id);
        when(c.getName()).thenReturn(name);
        return c;
    }
}
