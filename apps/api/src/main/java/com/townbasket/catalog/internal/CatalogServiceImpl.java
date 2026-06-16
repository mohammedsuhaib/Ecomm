package com.townbasket.catalog.internal;

import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.CategoryDto;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.shared.PagedResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link CatalogService}. Maps JPA entities to
 * public DTOs so entities never cross the module boundary.
 *
 * <p>cost_price is read on the entity for internal use only and is intentionally
 * never copied into {@link ProductVariantDto}.
 */
@Service
@Transactional(readOnly = true)
class CatalogServiceImpl implements CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    CatalogServiceImpl(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    public List<CategoryDto> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(CatalogServiceImpl::toCategoryDto)
                .toList();
    }

    @Override
    public PagedResponse<ProductDto> listProducts(Long categoryId, Pageable pageable) {
        Page<ProductEntity> page = (categoryId != null)
                ? productRepository.findByCategoryId(categoryId, pageable)
                : productRepository.findAll(pageable);
        return PagedResponse.of(page, CatalogServiceImpl::toProductDto);
    }

    @Override
    public Optional<ProductDto> findProduct(String idOrSlug) {
        Optional<ProductEntity> entity = parseLong(idOrSlug)
                .flatMap(productRepository::findById)
                .or(() -> productRepository.findBySlug(idOrSlug));
        return entity.map(CatalogServiceImpl::toProductDto);
    }

    @Override
    public PagedResponse<ProductDto> search(String query, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return new PagedResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
        }
        return PagedResponse.of(productRepository.search(q, pageable), CatalogServiceImpl::toProductDto);
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static CategoryDto toCategoryDto(CategoryEntity e) {
        return new CategoryDto(e.getId(), e.getName(), e.getSlug(), e.getImageUrl(), e.getSortOrder());
    }

    private static ProductDto toProductDto(ProductEntity e) {
        List<ProductVariantDto> variants = e.getVariants().stream()
                .map(CatalogServiceImpl::toVariantDto)
                .toList();
        return new ProductDto(
                e.getId(),
                e.getName(),
                e.getSlug(),
                e.getCategoryId(),
                e.getDescription(),
                e.isVegMarker(),
                e.getImageUrl(),
                e.isAvailable(),
                variants);
    }

    private static ProductVariantDto toVariantDto(ProductVariantEntity v) {
        // NOTE: cost_price (v.getCostPrice()) is intentionally NOT mapped — internal only.
        return new ProductVariantDto(
                v.getId(),
                v.getLabel(),
                v.getSellingPrice(),
                v.getMrp(),
                v.isAvailable());
    }
}
