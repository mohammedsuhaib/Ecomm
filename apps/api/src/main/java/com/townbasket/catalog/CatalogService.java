package com.townbasket.catalog;

import com.townbasket.shared.PagedResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

/**
 * Published API of the catalog module. Read-only at M2.
 *
 * <p>Returns DTOs only — entities and repositories stay module-internal.
 */
public interface CatalogService {

    List<CategoryDto> listCategories();

    PagedResponse<ProductDto> listProducts(Long categoryId, Pageable pageable);

    /** Look up a product by numeric id or by slug. */
    Optional<ProductDto> findProduct(String idOrSlug);

    PagedResponse<ProductDto> search(String query, Pageable pageable);
}
