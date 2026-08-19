package com.townbasket.catalog.internal;

import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.CreateProductRequest;
import com.townbasket.catalog.CreateVariantRequest;
import com.townbasket.catalog.ProductImportResult;
import com.townbasket.catalog.ProductImportResult.RowError;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.tax.TaxService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * CSV bulk import for products, one row per VARIANT: rows sharing a product
 * name become one product with several variants (first row's product-level
 * fields win). Deliberately NOT part of {@link CatalogServiceImpl}: it calls
 * the {@link CatalogService} proxy so each product is created in its own
 * write transaction — one bad product is reported, the rest still land.
 * Imports are re-runnable: an existing product (same name) is skipped.
 *
 * <p>Header (case-insensitive; order free):
 * required {@code name, category, variant_label, selling_price, cost_price};
 * optional {@code mrp, name_kn, description, veg, hsn, gst_rate, image_url}.
 * {@code category} matches a category by slug or name (case-insensitive).
 */
@Service
public class ProductCsvImporter {

    private static final Set<String> REQUIRED =
            Set.of("name", "category", "variant_label", "selling_price", "cost_price");
    private static final Set<String> OPTIONAL =
            Set.of("mrp", "name_kn", "description", "veg", "hsn", "gst_rate", "image_url");
    private static final int MAX_ROWS = 2000;

    private final CatalogService catalogService;
    private final TaxService taxService;
    private final ProductRepository products;
    private final CategoryRepository categories;

    ProductCsvImporter(CatalogService catalogService, TaxService taxService,
                       ProductRepository products, CategoryRepository categories) {
        this.catalogService = catalogService;
        this.taxService = taxService;
        this.products = products;
        this.categories = categories;
    }

    public ProductImportResult importCsv(String csv, boolean dryRun) {
        List<List<String>> lines = parseCsv(csv);
        if (lines.size() < 2) {
            throw new BusinessRuleException("CSV must have a header line and at least one data row.");
        }
        if (lines.size() - 1 > MAX_ROWS) {
            throw new BusinessRuleException("Too many rows (max " + MAX_ROWS + " per import).");
        }
        Map<String, Integer> col = headerIndex(lines.get(0));

        // Category lookup by slug or lowercase name.
        Map<String, Long> categoryIds = new HashMap<>();
        for (CategoryEntity c : categories.findAll()) {
            categoryIds.put(c.getSlug().toLowerCase(Locale.ROOT), c.getId());
            categoryIds.put(c.getName().trim().toLowerCase(Locale.ROOT), c.getId());
        }

        // Group data rows by product name (order-preserving, non-consecutive ok).
        Map<String, List<Row>> byProduct = new LinkedHashMap<>();
        List<RowError> errors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = lines.get(i);
            if (cells.size() == 1 && cells.get(0).isBlank()) {
                continue; // blank line
            }
            if (cells.size() != col.size()) {
                errors.add(new RowError(i + 1, "Expected " + col.size() + " columns, got " + cells.size() + "."));
                continue;
            }
            Row row = new Row(i + 1, cells, col);
            String name = row.get("name");
            if (name.isBlank()) {
                errors.add(new RowError(row.line, "Product name is blank."));
                continue;
            }
            byProduct.computeIfAbsent(name.trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(row);
        }

        int created = 0;
        int skipped = 0;
        for (List<Row> group : byProduct.values()) {
            Row first = group.get(0);
            String name = first.get("name").trim();
            if (products.existsByNameIgnoreCase(name)) {
                skipped++;
                continue;
            }
            try {
                CreateProductRequest request = toRequest(name, group, categoryIds);
                if (!dryRun) {
                    catalogService.createProduct(request);
                }
                created++;
            } catch (BusinessRuleException | IllegalArgumentException e) {
                errors.add(new RowError(first.line, name + ": " + e.getMessage()));
            }
        }
        return new ProductImportResult(created, skipped, List.copyOf(errors));
    }

    private CreateProductRequest toRequest(String name, List<Row> group, Map<String, Long> categoryIds) {
        Row first = group.get(0);

        String categoryRef = first.get("category").trim();
        Long categoryId = categoryIds.get(categoryRef.toLowerCase(Locale.ROOT));
        if (categoryId == null) {
            throw new BusinessRuleException("Unknown category '" + categoryRef + "'.");
        }

        BigDecimal gstRate = decimalOrNull(first.get("gst_rate"), "gst_rate");
        if (gstRate != null) {
            taxService.requireValidGstRate(gstRate);
        }

        List<CreateVariantRequest> variants = new ArrayList<>();
        int sortOrder = 0;
        for (Row row : group) {
            String label = row.get("variant_label").trim();
            if (label.isBlank()) {
                throw new BusinessRuleException("Row " + row.line + ": variant_label is blank.");
            }
            variants.add(new CreateVariantRequest(
                    label,
                    requireDecimal(row.get("selling_price"), "selling_price", row.line),
                    requireDecimal(row.get("cost_price"), "cost_price", row.line),
                    decimalOrNull(row.get("mrp"), "mrp"),
                    null,
                    sortOrder++));
        }

        return new CreateProductRequest(
                name,
                blankToNull(first.get("name_kn")),
                null, // slug auto-generated
                categoryId,
                blankToNull(first.get("description")),
                parseVeg(first.get("veg")),
                blankToNull(first.get("image_url")),
                null, // available: default true
                null, // featured: default false
                blankToNull(first.get("hsn")),
                gstRate,
                variants);
    }

    // ---- header + cell helpers -------------------------------------------

    private static Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                col.put(key, i);
            }
        }
        List<String> missing = REQUIRED.stream().filter(c -> !col.containsKey(c)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Missing required column(s): " + String.join(", ", missing)
                    + ". Expected header: name, category, variant_label, selling_price, cost_price"
                    + " (+ optional: " + String.join(", ", OPTIONAL.stream().sorted().toList()) + ").");
        }
        return col;
    }

    private record Row(int line, List<String> cells, Map<String, Integer> col) {
        String get(String column) {
            Integer i = col.get(column);
            return (i == null || i >= cells.size()) ? "" : cells.get(i);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Boolean parseVeg(String s) {
        if (s == null || s.isBlank()) {
            return null; // default (veg)
        }
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "y", "yes", "true", "veg", "1" -> true;
            case "n", "no", "false", "non-veg", "nonveg", "0" -> false;
            default -> throw new BusinessRuleException("veg must be Y or N (got '" + s.trim() + "').");
        };
    }

    private static BigDecimal requireDecimal(String s, String column, int line) {
        BigDecimal value = decimalOrNull(s, column);
        if (value == null) {
            throw new BusinessRuleException("Row " + line + ": " + column + " is required.");
        }
        return value;
    }

    private static BigDecimal decimalOrNull(String s, String column) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim().replace("₹", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new BusinessRuleException(column + " is not a number (got '" + s.trim() + "').");
        }
    }

    // ---- minimal RFC-4180 CSV parser (quoted fields, "" escapes, CRLF) ----

    private static List<List<String>> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new BusinessRuleException("The uploaded file is empty.");
        }
        List<List<String>> lines = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(cell.toString());
                cell.setLength(0);
                lines.add(List.copyOf(cells));
                cells.clear();
            } else {
                cell.append(ch);
            }
        }
        if (quoted) {
            throw new BusinessRuleException("Unterminated quoted field in CSV.");
        }
        cells.add(cell.toString());
        if (cells.size() > 1 || !cells.get(0).isBlank()) {
            lines.add(List.copyOf(cells));
        }
        return lines;
    }
}
