package com.townbasket.tax.internal;

import com.townbasket.shared.BusinessRuleException;
import com.townbasket.tax.TaxBreakdown;
import com.townbasket.tax.TaxService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Module-internal {@link TaxService} implementation.
 *
 * <p>Back-calculation: for gross {@code G} at rate {@code r}%, the taxable
 * value is {@code G × 100 / (100 + r)} rounded half-up to paise; the tax is
 * whatever remains of {@code G}, split half to CGST (rounded half-up) with
 * SGST absorbing the last paisa — so the three parts always re-add to
 * {@code G} exactly.
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
        BigDecimal taxable = gross.multiply(HUNDRED)
                .divide(HUNDRED.add(ratePercent), 2, RoundingMode.HALF_UP);
        BigDecimal tax = gross.subtract(taxable);
        BigDecimal cgst = tax.divide(TWO, 2, RoundingMode.HALF_UP);
        BigDecimal sgst = tax.subtract(cgst); // absorbs the odd paisa
        return new TaxBreakdown(taxable, cgst, sgst);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }
}
