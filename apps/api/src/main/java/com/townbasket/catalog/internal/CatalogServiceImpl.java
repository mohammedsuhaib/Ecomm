package com.townbasket.catalog.internal;

import com.townbasket.catalog.AdminProductDto;
import com.townbasket.catalog.AdminVariantDto;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.CategoryDto;
import com.townbasket.catalog.CreateCategoryRequest;
import com.townbasket.catalog.CreateProductRequest;
import com.townbasket.catalog.CreateVariantRequest;
import com.townbasket.catalog.ProductDto;
import com.townbasket.catalog.ProductSort;
import com.townbasket.catalog.ProductVariantDto;
import com.townbasket.catalog.UpdateCategoryRequest;
import com.townbasket.catalog.UpdateProductRequest;
import com.townbasket.catalog.UpdateVariantRequest;
import com.townbasket.catalog.VariantView;
import com.townbasket.inventory.InventoryService;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.PagedResponse;
import com.townbasket.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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
     * Inventory lookup so storefront product responses carry the live sellable
     * count per variant (out-of-stock should show in the catalogue, not only in
     * the cart). Read-only and batched per request to avoid an N+1 across a page.
     */
    private final InventoryService inventory;

    /**
     * Optional because the bean only exists when
     * {@code townbasket.catalog.transliteration.enabled=true}. When present, the
     * admin create/update path best-effort fills {@code name_kn} from the English
     * name unless the caller supplied one explicitly.
     */
    private final Optional<ProductNameTransliterator> transliterator;

    /** Default gap between auto-assigned category sort orders. */
    private static final int SORT_ORDER_GAP = 10;

    /** Matches runs of non-alphanumeric characters for slug generation. */
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    /** Strips diacritics during slug generation so "Café" -> "cafe". */
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

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
                       ProductVariantRepository variantRepository,
                       InventoryService inventory,
                       Optional<ProductNameTransliterator> transliterator) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.inventory = inventory;
        this.transliterator = transliterator;
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
        Map<Long, Integer> stock = stockFor(page.getContent());
        return PagedResponse.of(page, e -> toProductDto(e, stock));
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
        return entity.map(e -> toProductDto(e, stockFor(List.of(e))));
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
        Page<ProductEntity> page = productRepository.search(q, pageable);
        Map<Long, Integer> stock = stockFor(page.getContent());
        return PagedResponse.of(page, e -> toProductDto(e, stock));
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
    private PagedResponse<ProductDto> pageInMemory(List<ProductEntity> sorted, Pageable pageable) {
        int total = sorted.size();
        int size = pageable.getPageSize();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + size, total);
        List<ProductEntity> slice = sorted.subList(from, to);
        Map<Long, Integer> stock = stockFor(slice);
        Page<ProductEntity> page = new PageImpl<>(slice, pageable, total);
        return PagedResponse.of(page, e -> toProductDto(e, stock));
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

    // ----------------------------------------------------------------------
    // Admin write surface. All methods here are read-write transactions (the
    // class default is readOnly=true, so each one re-declares @Transactional).
    // ----------------------------------------------------------------------

    @Override
    @Transactional
    public List<CategoryDto> adminListCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(CatalogServiceImpl::toCategoryDto)
                .toList();
    }

    @Override
    @Transactional
    public CategoryDto createCategory(CreateCategoryRequest request) {
        String name = requireText(request.name(), "Category name must not be blank.");
        String slug = resolveSlug(request.slug(), name, this::categorySlugExists);
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : nextCategorySortOrder();
        CategoryEntity saved = categoryRepository.save(
                CategoryEntity.create(name, slug, sortOrder, trimToNull(request.imageUrl())));
        return toCategoryDto(saved);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long id, UpdateCategoryRequest request) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> categoryNotFound(id));
        category.setName(requireText(request.name(), "Category name must not be blank."));
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.imageUrl() != null) {
            category.setImageUrl(trimToNull(request.imageUrl()));
        }
        // Slug is immutable — intentionally not touched.
        return toCategoryDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> categoryNotFound(id));
        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessRuleException(
                    "Category still has products and cannot be deleted. "
                            + "Move or delete its products first.");
        }
        categoryRepository.delete(category);
    }

    @Override
    @Transactional
    public PagedResponse<AdminProductDto> adminListProducts(Long categoryId, String q, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        Page<ProductEntity> page;
        if (!query.isEmpty()) {
            page = categoryId != null
                    ? productRepository.findByCategoryIdAndNameContainingIgnoreCase(categoryId, query, pageable)
                    : productRepository.findByNameContainingIgnoreCase(query, pageable);
        } else if (categoryId != null) {
            page = productRepository.findByCategoryId(categoryId, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }
        return PagedResponse.of(page, this::toAdminProductDto);
    }

    @Override
    @Transactional
    public AdminProductDto adminGetProduct(Long id) {
        return toAdminProductDto(requireProduct(id));
    }

    @Override
    @Transactional
    public AdminProductDto createProduct(CreateProductRequest request) {
        String name = requireText(request.name(), "Product name must not be blank.");
        Long categoryId = requireCategory(request.categoryId());
        String slug = resolveSlug(request.slug(), name, this::productSlugExists);

        boolean vegMarker = request.vegMarker() == null || request.vegMarker();
        boolean available = request.available() == null || request.available();
        boolean featured = request.featured() != null && request.featured();

        // name_kn: explicit value wins; otherwise best-effort transliterate.
        String nameKn = trimToNull(request.nameKn());
        if (nameKn == null) {
            nameKn = transliterate(name);
        }

        ProductEntity product = ProductEntity.create(
                categoryId, name, nameKn, slug,
                trimToNull(request.description()), vegMarker,
                trimToNull(request.imageUrl()), available, featured);

        if (request.variants() != null) {
            for (CreateVariantRequest v : request.variants()) {
                product.getVariants().add(buildVariant(v));
            }
        }

        // saveAndFlush so the cascade-inserted variants' IDENTITY ids are
        // assigned before we map them into the response DTO.
        return toAdminProductDto(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public AdminProductDto updateProduct(Long id, UpdateProductRequest request) {
        ProductEntity product = requireProduct(id);
        String name = requireText(request.name(), "Product name must not be blank.");
        Long categoryId = requireCategory(request.categoryId());

        boolean nameChanged = !name.equals(product.getName());
        product.setName(name);
        product.setCategoryId(categoryId);
        if (request.description() != null) {
            product.setDescription(trimToNull(request.description()));
        }
        if (request.vegMarker() != null) {
            product.setVegMarker(request.vegMarker());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(trimToNull(request.imageUrl()));
        }
        if (request.available() != null) {
            product.setAvailable(request.available());
        }
        if (request.featured() != null) {
            product.setFeatured(request.featured());
        }

        // name_kn: an explicit value always wins. Otherwise, if the English name
        // changed, best-effort re-transliterate; an unchanged name keeps the stored one.
        String explicitKn = trimToNull(request.nameKn());
        if (explicitKn != null) {
            product.setNameKn(explicitKn);
        } else if (nameChanged) {
            String generated = transliterate(name);
            if (generated != null) {
                product.setNameKn(generated);
            }
        }
        // Slug is immutable — intentionally not touched.
        return toAdminProductDto(productRepository.save(product));
    }

    @Override
    @Transactional
    public AdminProductDto setProductAvailability(Long id, boolean available) {
        ProductEntity product = requireProduct(id);
        product.setAvailable(available);
        return toAdminProductDto(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        ProductEntity product = requireProduct(id);
        // Hard delete; CascadeType.ALL on the variants association removes them too.
        productRepository.delete(product);
    }

    @Override
    @Transactional
    public AdminVariantDto addVariant(Long productId, CreateVariantRequest request) {
        ProductEntity product = requireProduct(productId);
        ProductVariantEntity variant = buildVariant(request);
        product.getVariants().add(variant);
        // `product` is a MANAGED entity (loaded in this transaction), so flushing
        // cascade-persists the new variant onto this very instance and assigns its
        // IDENTITY id. Do NOT use save() here: the product already has an id, so
        // save() does an em.merge() which copies the transient variant — the id
        // would land on the copy, leaving our reference's id null.
        productRepository.flush();
        return toAdminVariantDto(variant);
    }

    @Override
    @Transactional
    public AdminVariantDto updateVariant(Long productId, Long variantId, UpdateVariantRequest request) {
        ProductVariantEntity variant = requireVariant(productId, variantId);
        variant.setLabel(requireText(request.label(), "Variant label must not be blank."));
        variant.setSellingPrice(requireNonNegative(request.sellingPrice(), "Selling price"));
        variant.setCostPrice(requireNonNegative(request.costPrice(), "Cost price"));
        variant.setMrp(request.mrp());
        if (request.available() != null) {
            variant.setAvailable(request.available());
        }
        if (request.sortOrder() != null) {
            variant.setSortOrder(request.sortOrder());
        }
        variantRepository.save(variant);
        return toAdminVariantDto(variant);
    }

    @Override
    @Transactional
    public AdminVariantDto setVariantAvailability(Long productId, Long variantId, boolean available) {
        ProductVariantEntity variant = requireVariant(productId, variantId);
        variant.setAvailable(available);
        variantRepository.save(variant);
        return toAdminVariantDto(variant);
    }

    @Override
    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        ProductVariantEntity variant = requireVariant(productId, variantId);
        // Remove from the owning collection too so an in-flight entity graph stays
        // consistent, then delete the row.
        ProductEntity product = requireProduct(productId);
        product.getVariants().removeIf(v -> variantId.equals(v.getId()));
        variantRepository.delete(variant);
    }

    // ---- admin helpers --------------------------------------------------

    /** Build a new variant entity from a create request, validating + defaulting. */
    private static ProductVariantEntity buildVariant(CreateVariantRequest request) {
        String label = requireText(request.label(), "Variant label must not be blank.");
        BigDecimal sellingPrice = requireNonNegative(request.sellingPrice(), "Selling price");
        BigDecimal costPrice = requireNonNegative(request.costPrice(), "Cost price");
        boolean available = request.available() == null || request.available();
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
        return ProductVariantEntity.create(label, sellingPrice, costPrice, request.mrp(), available, sortOrder);
    }

    private ProductEntity requireProduct(Long id) {
        if (id == null) {
            throw new ResourceNotFoundException("Product not found: null");
        }
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private Long requireCategory(Long categoryId) {
        if (categoryId == null || !categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found: " + categoryId);
        }
        return categoryId;
    }

    /** Load a variant and assert it belongs to the given product. */
    private ProductVariantEntity requireVariant(Long productId, Long variantId) {
        requireProduct(productId);
        ProductVariantEntity variant = variantId == null ? null
                : variantRepository.findById(variantId).orElse(null);
        if (variant == null || !productId.equals(variant.getProductId())) {
            throw new ResourceNotFoundException(
                    "Variant not found: " + variantId + " for product " + productId);
        }
        return variant;
    }

    private static ResourceNotFoundException categoryNotFound(Long id) {
        return new ResourceNotFoundException("Category not found: " + id);
    }

    private static String requireText(String value, String message) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BusinessRuleException(message);
        }
        return trimmed;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new BusinessRuleException(field + " is required.");
        }
        if (value.signum() < 0) {
            throw new BusinessRuleException(field + " must not be negative.");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Best-effort transliteration of an English name to Kannada; null on absence/failure. */
    private String transliterate(String name) {
        if (transliterator.isEmpty() || name == null || name.isBlank()) {
            return null;
        }
        try {
            return transliterator.get().toKannada(name).orElse(null);
        } catch (RuntimeException ignored) {
            // Best-effort only — never fail a write because transliteration is unavailable.
            return null;
        }
    }

    private int nextCategorySortOrder() {
        return categoryRepository.findAll().stream()
                .mapToInt(CategoryEntity::getSortOrder)
                .max()
                .stream()
                .map(max -> max + SORT_ORDER_GAP)
                .findFirst()
                .orElse(0);
    }

    private boolean categorySlugExists(String slug) {
        return categoryRepository.findBySlug(slug).isPresent();
    }

    private boolean productSlugExists(String slug) {
        return productRepository.findBySlug(slug).isPresent();
    }

    /**
     * Resolve a final slug: use the caller's slug if given (slugified), else derive
     * from {@code name}. On collision append {@code -2}, {@code -3}, … until free.
     */
    private static String resolveSlug(String requested, String name,
                                      java.util.function.Predicate<String> exists) {
        String base = slugify(trimToNull(requested) != null ? requested : name);
        if (base.isEmpty()) {
            base = "item";
        }
        String candidate = base;
        int suffix = 2;
        while (exists.test(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    /**
     * Slugify: lowercase, strip diacritics, collapse runs of non-alphanumerics into
     * single hyphens, and trim leading/trailing hyphens.
     */
    private static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        String lowered = normalized.toLowerCase(java.util.Locale.ROOT).trim();
        String hyphenated = NON_ALNUM.matcher(lowered).replaceAll("-");
        // Strip leading/trailing hyphens.
        int start = 0;
        int end = hyphenated.length();
        while (start < end && hyphenated.charAt(start) == '-') {
            start++;
        }
        while (end > start && hyphenated.charAt(end - 1) == '-') {
            end--;
        }
        return hyphenated.substring(start, end);
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

    /**
     * Collect live availability for every variant across the given products in a
     * single inventory query. Missing variants are absent from the map and read
     * back as 0 stock (see {@link #toVariantDto}).
     */
    private Map<Long, Integer> stockFor(Collection<ProductEntity> products) {
        List<Long> variantIds = products.stream()
                .flatMap(p -> p.getVariants().stream())
                .map(ProductVariantEntity::getId)
                .toList();
        return inventory.availability(variantIds);
    }

    private ProductDto toProductDto(ProductEntity e, Map<Long, Integer> stock) {
        List<ProductVariantDto> variants = e.getVariants().stream()
                .map(v -> toVariantDto(v, stock))
                .toList();
        return new ProductDto(
                e.getId(),
                e.getName(),
                e.getNameKn(),
                e.getSlug(),
                e.getCategoryId(),
                e.getDescription(),
                e.isVegMarker(),
                e.getImageUrl(),
                e.isAvailable(),
                e.isFeatured(),
                variants);
    }

    private static ProductVariantDto toVariantDto(ProductVariantEntity v, Map<Long, Integer> stock) {
        // NOTE: cost_price (v.getCostPrice()) is intentionally NOT mapped — internal only.
        return new ProductVariantDto(
                v.getId(),
                v.getLabel(),
                v.getSellingPrice(),
                v.getMrp(),
                v.isAvailable(),
                stock.getOrDefault(v.getId(), 0));
    }

    /** Admin product mapping — includes cost price + the resolved category name. */
    private AdminProductDto toAdminProductDto(ProductEntity e) {
        String categoryName = categoryRepository.findById(e.getCategoryId())
                .map(CategoryEntity::getName)
                .orElse(null);
        List<AdminVariantDto> variants = e.getVariants().stream()
                .map(CatalogServiceImpl::toAdminVariantDto)
                .toList();
        return new AdminProductDto(
                e.getId(),
                e.getName(),
                e.getNameKn(),
                e.getSlug(),
                e.getCategoryId(),
                categoryName,
                e.getDescription(),
                e.isVegMarker(),
                e.getImageUrl(),
                e.isAvailable(),
                e.isFeatured(),
                variants);
    }

    /** Admin variant mapping — DOES include cost price (staff-only surface). */
    private static AdminVariantDto toAdminVariantDto(ProductVariantEntity v) {
        return new AdminVariantDto(
                v.getId(),
                v.getLabel(),
                v.getSellingPrice(),
                v.getCostPrice(),
                v.getMrp(),
                v.isAvailable(),
                v.getSortOrder());
    }
}
