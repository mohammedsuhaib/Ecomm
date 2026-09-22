package com.townbasket.orders.internal;

import com.townbasket.orders.SalesReportService;
import com.townbasket.serviceability.ServiceabilityService;
import com.townbasket.serviceability.StoreDto;
import com.townbasket.shared.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches the report rows and hands them to the format-specific renderers.
 * Row-fetching lives here (one native query per {@link OrderRepository});
 * byte generation is split out per {@link SalesReportXlsxGenerator} and
 * {@link SalesReportPdfGenerator}, mirroring {@code InvoicePdfGenerator}'s
 * single-responsibility split.
 */
@Service
class SalesReportServiceImpl implements SalesReportService {

    private final OrderRepository orders;
    private final ServiceabilityService serviceability;
    private final SalesReportXlsxGenerator xlsxGenerator;
    private final SalesReportPdfGenerator pdfGenerator;
    private final Clock clock;

    SalesReportServiceImpl(OrderRepository orders, ServiceabilityService serviceability,
                            SalesReportXlsxGenerator xlsxGenerator,
                            SalesReportPdfGenerator pdfGenerator, Clock clock) {
        this.orders = orders;
        this.serviceability = serviceability;
        this.xlsxGenerator = xlsxGenerator;
        this.pdfGenerator = pdfGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderSalesReportXlsx(LocalDate from, LocalDate to) {
        return xlsxGenerator.render(rows(from, to), from, to, currentGstin());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderSalesReportPdf(LocalDate from, LocalDate to) {
        return pdfGenerator.render(rows(from, to), from, to, currentGstin());
    }

    /**
     * The store's GSTIN as of right now — not the historical value pinned on
     * any one order. A report spans a whole date range, so it shows the
     * registration filing against it applies today, the same way the store
     * settings card and the storefront footer do.
     */
    private String currentGstin() {
        return serviceability.activeStore().map(StoreDto::gstin).orElse(null);
    }

    /**
     * Resolves the inclusive local-date range to a store-day instant window
     * (same construction as {@code OrderServiceImpl#startOfStoreDay}: midnight
     * in the store's own zone, not UTC, so a shift that runs into the evening
     * isn't split across two report days) and loads the matching lines.
     */
    private List<OrderRepository.SalesReportRow> rows(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("'to' date must not be before 'from' date.");
        }
        ZoneId zone = clock.getZone();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        return orders.salesReportRows(start, end);
    }
}
