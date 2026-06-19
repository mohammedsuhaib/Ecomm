package com.townbasket.catalog.internal;

import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.CategoryDto;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductSort;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.catalog.VariantView;
import com.townbasket.shared.PagedResponse;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final ProductVariantRepository variantRepository;

    /**
     * Upper bound on how many matching products the in-service sort path loads.
     * The {@code ?sort=} / {@code ?q=&sort=} paths sort + paginate in memory
     * (price/discount span variants, so a single SQL ORDER BY is awkward); this
     * cap stops a public sorted request from pulling the WHOLE catalog into
     * memory (and triggering a lazy-variant N+1 per row). Sorting therefore
     * considers at most the first {@code MAX_SORTABLE} matches — ample for this
     * single-store catalog. If the catalog ever outgrows it, push the sort into
     * SQL (e.g. a denormalised min_selling_price / max_discount column) rather
     * than raising this number.
     */
    private static final int MAX_SORTABLE = 1000;

    CatalogServiceImpl(CategoryRepository categoryRepository,
                       ProductRepository productRepository,
                       ProductVariantRepository variantRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    @Override
    public List<CategoryDto> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(CatalogServiceImpl::toCategoryDto)
                .toList();
    }

    @Override
    public PagedResponse<ProductDto> listProducts(Long categoryId, boolean featured, ProductSort sort, Pageable pageable) {
        if (sort != null) {
            // Sort the filtered set, then page in memory. price/discount sorts
            // span variants, so a single SQL ORDER BY is awkward. The fetch is
            // CAPPED at MAX_SORTABLE so a public ?sort= can't load the whole
            // catalog (memory + lazy-variant N+1).
            List<ProductEntity> all = findAllFiltered(categoryId, featured);
            return pageInMemory(sortEntities(all, sort), pageable);
        }
        Page<ProductEntity> page = pagedFiltered(categoryId, featured, pageable);
        return PagedResponse.of(page, CatalogServiceImpl::toProductDto);
    }

    private Page<ProductEntity> pagedFiltered(Long categoryId, boolean featured, Pageable pageable) {
        if (categoryId != null) {
            return featured
                    ? productRepository.findByCategoryIdAndFeaturedTrue(categoryId, pageable)
                    : productRepository.findByCategoryId(categoryId, pageable);
        }
        return featured
                ? productRepository.findByFeaturedTrue(pageable)
                : productRepository.findAll(pageable);
    }

    private List<ProductEntity> findAllFiltered(Long categoryId, boolean featured) {
        return pagedFiltered(categoryId, featured, PageRequest.of(0, MAX_SORTABLE, Sort.by("id"))).getContent();
    }

    @Override
    public Optional<ProductDto> findProduct(String idOrSlug) {
        Optional<ProductEntity> entity = parseLong(idOrSlug)
                .flatMap(productRepository::findById)
                .or(() -> productRepository.findBySlug(idOrSlug));
        return entity.map(CatalogServiceImpl::toProductDto);
    }

    @Override
    public PagedResponse<ProductDto> search(String query, ProductSort sort, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return new PagedResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
        }
        if (sort != null) {
            // Re-rank the relevance-ordered match set, then page in memory.
            // Capped at MAX_SORTABLE (see field doc) to bound a public sorted search.
            List<ProductEntity> all = productRepository.search(q, PageRequest.of(0, MAX_SORTABLE)).getContent();
            return pageInMemory(sortEntities(all, sort), pageable);
        }
        return PagedResponse.of(productRepository.search(q, pageable), CatalogServiceImpl::toProductDto);
    }

    /** Apply the requested {@link ProductSort} to the full filtered list. */
    private static List<ProductEntity> sortEntities(List<ProductEntity> products, ProductSort sort) {
        Comparator<ProductEntity> comparator = switch (sort) {
            case NAME -> Comparator.comparing(p -> p.getName() == null ? "" : p.getName(),
                    String.CASE_INSENSITIVE_ORDER);
            // Ascending by the product's LOWEST available variant selling price.
            case PRICE_ASC -> Comparator.comparing(CatalogServiceImpl::lowestAvailablePrice);
            // Descending by that same lowest available variant selling price.
            case PRICE_DESC -> Comparator.comparing(CatalogServiceImpl::lowestAvailablePrice).reversed();
            // Descending by the product's MAX variant discount (mrp - sellingPrice).
            case DISCOUNT -> Comparator.comparing(CatalogServiceImpl::maxDiscount).reversed();
        };
        // Stable tie-break on id so paging is deterministic.
        return products.stream()
                .sorted(comparator.thenComparing(ProductEntity::getId))
                .toList();
    }

    /**
     * Lowest selling price across a product's AVAILABLE variants. Products with no
     * available variant sort last for {@code price_asc} (treated as +∞).
     */
    private static BigDecimal lowestAvailablePrice(ProductEntity p) {
        return p.getVariants().stream()
                .filter(ProductVariantEntity::isAvailable)
                .map(ProductVariantEntity::getSellingPrice)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.valueOf(Long.MAX_VALUE));
    }

    /**
     * Maximum discount ({@code mrp - sellingPrice}) across a product's variants;
     * a null mrp counts as zero discount. No variants => zero discount.
     */
    private static BigDecimal maxDiscount(ProductEntity p) {
        return p.getVariants().stream()
                .map(v -> {
                    BigDecimal mrp = v.getMrp();
                    if (mrp == null) {
                        return BigDecimal.ZERO;
                    }
                    BigDecimal discount = mrp.subtract(v.getSellingPrice());
                    return discount.signum() < 0 ? BigDecimal.ZERO : discount;
                })
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    /** Slice an already-sorted list into the requested page and wrap as a {@link PagedResponse}. */
    private static PagedResponse<ProductDto> pageInMemory(List<ProductEntity> sorted, Pageable pageable) {
        int total = sorted.size();
        int size = pageable.getPageSize();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + size, total);
        Page<ProductEntity> page = new PageImpl<>(sorted.subList(from, to), pageable, total);
        return PagedResponse.of(page, CatalogServiceImpl::toProductDto);
    }

    @Override
    public Optional<VariantView> findVariant(Long variantId) {
        if (variantId == null) {
            return Optional.empty();
        }
        return variantRepository.findById(variantId).map(v -> {
            String productName = productRepository.findById(v.getProductId())
                    .map(ProductEntity::getName)
                    .orElse(null);
            return new VariantView(
                    v.getId(),
                    v.getProductId(),
                    productName,
                    v.getLabel(),
                    v.getSellingPrice(),
                    v.isAvailable());
        });
    }

    @Override
    public Optional<BigDecimal> costPrice(Long variantId) {
        if (variantId == null) {
            return Optional.empty();
        }
        // INTERNAL: cost price for the orders COGS snapshot only — never serialized.
        return variantRepository.findById(variantId).map(ProductVariantEntity::getCostPrice);
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
                e.isFeatured(),
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
