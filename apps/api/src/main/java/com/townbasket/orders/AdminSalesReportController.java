package com.townbasket.orders;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin GST sales-report export, under {@code /api/v1/admin/orders/reports}.
 *
 * <p>Secured by the existing STORE_STAFF | ADMIN rule on {@code /api/v1/admin/**}
 * (see {@code SecurityConfig}) — no per-method {@code @PreAuthorize} needed.
 *
 * <p>Kept as its own controller class rather than folded into
 * {@link AdminOrderController}: it is a document export, not a queue
 * operation, and depends on a different (small) service abstraction.
 */
@RestController
@RequestMapping("/api/v1/admin/orders/reports")
@Tag(name = "Admin Orders", description = "GST sales report export.")
class AdminSalesReportController {

    private static final DateTimeFormatter FILENAME_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SalesReportService salesReportService;

    AdminSalesReportController(SalesReportService salesReportService) {
        this.salesReportService = salesReportService;
    }

    @GetMapping("/sales")
    @Operation(summary = "Export a GST sales report (one row per order item line) for a date range, as xlsx or pdf.")
    ResponseEntity<byte[]> salesReport(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam String format) {
        String filenameBase = "sales-report-" + FILENAME_DATE.format(from) + "-to-" + FILENAME_DATE.format(to);
        return switch (format == null ? "" : format.toLowerCase()) {
            case "xlsx" -> {
                byte[] body = salesReportService.renderSalesReportXlsx(from, to);
                yield ResponseEntity.ok()
                        .contentType(XLSX)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filenameBase + ".xlsx\"")
                        .body(body);
            }
            case "pdf" -> {
                byte[] body = salesReportService.renderSalesReportPdf(from, to);
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filenameBase + ".pdf\"")
                        .body(body);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported format '" + format + "' — expected 'xlsx' or 'pdf'.");
        };
    }
}
