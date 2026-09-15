package com.townbasket.tax.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.tax.HsnSuggestion;
import com.townbasket.tax.TaxBreakdown;
import com.townbasket.tax.TaxService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Module-internal {@link TaxService} implementation.
 *
 * <p><strong>Back-calculation.</strong> For gross {@code G} at combined rate
 * {@code r}%, CGST and SGST are each computed directly as
 * {@code G × (r/2) / (100 + r)} — the half-rate levy the statute actually
 * describes, since an intra-state supply at 18% is 9% central tax and 9% State
 * tax on the same value, not one 18% tax cut in two. The taxable value is then
 * whatever remains of {@code G}, so the three parts re-add to {@code G}
 * exactly.
 *
 * <p><strong>Why this shape.</strong> Deriving the tax first and halving it
 * cannot always split evenly — ₹600 at 18% carries ₹91.53, an odd number of
 * paise — so one side had to take the extra paisa, and since the choice was
 * made per line the same side won every time. The bias then accumulated down
 * an invoice: two such lines showed CGST ₹52.94 against SGST ₹52.92 (QA's
 * TC-TAX-005). Computing both halves from one expression removes the choice
 * instead of managing it: they are equal on every line, so any invoice's
 * totals are equal however many lines it has.
 *
 * <p>The residue lands on the taxable value instead, up to a paisa off the
 * pure {@code G × 100 / (100 + r)}. That is the right place for it: the
 * taxable value has no exact two-decimal form anyway, and nothing requires it
 * to be halved — whereas CGST and SGST are, by construction, halves.
 */
@Service
class GstCalculator implements TaxService {

    private static final List<BigDecimal> SLABS = List.of(
            new BigDecimal("0"),
            new BigDecimal("5"),
            new BigDecimal("18"),
            new BigDecimal("40"));

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    @Override
    public List<BigDecimal> gstSlabs() {
        return SLABS;
    }

    @Override
    public boolean isValidGstRate(BigDecimal ratePercent) {
        return ratePercent != null
                && SLABS.stream().anyMatch(s -> s.compareTo(ratePercent) == 0);
    }

    @Override
    public void requireValidGstRate(BigDecimal ratePercent) {
        if (!isValidGstRate(ratePercent)) {
            throw new BusinessRuleException("GST rate must be one of "
                    + SLABS.stream().map(s -> s.toPlainString() + "%")
                            .collect(Collectors.joining(", "))
                    + " (got " + (ratePercent == null ? "none" : ratePercent.toPlainString() + "%") + ").");
        }
    }

    @Override
    public TaxBreakdown fromInclusiveAmount(BigDecimal grossAmount, BigDecimal ratePercent) {
        if (grossAmount == null || grossAmount.signum() < 0) {
            throw new BusinessRuleException("Amount to tax must be a non-negative value.");
        }
        requireValidGstRate(ratePercent);

        BigDecimal gross = grossAmount.setScale(2, RoundingMode.HALF_UP);
        if (ratePercent.signum() == 0) {
            return new TaxBreakdown(gross, zero(), zero());
        }
        // Each half-levy straight from the gross, in ONE division:
        //   half = G × (r/2) / (100 + r)
        // Deriving both halves from the same expression is what makes them
        // equal — there is no combined tax figure to divide, so no odd paisa to
        // award to one side. Computing it in one step also avoids rounding the
        // taxable value and then rounding again on the way to the half.
        BigDecimal half = gross.multiply(ratePercent)
                .divide(HUNDRED.add(ratePercent).multiply(TWO), 2, RoundingMode.HALF_UP);
        // The taxable value is what is left, so the three parts re-add to the
        // gross exactly. It can sit a paisa off the pure G × 100 / (100 + r) —
        // that figure has no exact two-decimal value either (₹600 at 18% is
        // ₹508.4745…), and of the two derived numbers it is the one with no
        // legal halving requirement on it.
        BigDecimal taxable = gross.subtract(half).subtract(half);
        return new TaxBreakdown(taxable, half, half);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }

    // ------------------------------------------------------------------
    // Curated HSN suggestions (tax/hsn-suggestions.json on the classpath).
    // Grocery-universe candidates with qualifier text; a suggestion aid for
    // the admin product form, never a compliance authority. Loaded once at
    // startup — fail fast on a malformed file.
    // ------------------------------------------------------------------

    private static final int MAX_SUGGESTIONS = 8;

    private final List<HsnSuggestion> curated;

    GstCalculator(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("tax/hsn-suggestions.json").getInputStream()) {
            this.curated = objectMapper.readValue(in, new TypeReference<List<HsnSuggestion>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load tax/hsn-suggestions.json", e);
        }
    }

    @Override
    public List<HsnSuggestion> hsnSuggestions(String hsnCode) {
        String query = hsnCode == null ? "" : hsnCode.replaceAll("\\D", "");
        if (query.isEmpty()) {
            return List.of();
        }
        return curated.stream()
                .filter(s -> query.startsWith(s.hsn()) || s.hsn().startsWith(query))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }
}
