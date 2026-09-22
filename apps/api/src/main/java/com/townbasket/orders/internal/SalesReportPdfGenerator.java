package com.townbasket.orders.internal;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Renders the sales-report rows into a landscape PDF using OpenPDF — the same
 * library, palette and header styling as {@link InvoicePdfGenerator}, so the
 * two documents read as one product. Pure presentation over
 * {@link OrderRepository.SalesReportRow}.
 */
@Component
class SalesReportPdfGenerator {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(IST);
    private static final BigDecimal TWO = new BigDecimal("2");

    // Same brand palette as InvoicePdfGenerator, on purpose.
    private static final Color BRAND = new Color(0x2E, 0x7D, 0x32);
    private static final Color INK = new Color(0x1A, 0x1A, 0x1A);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color LINE = new Color(0xE2, 0xE2, 0xE2);
    private static final Color ZEBRA = new Color(0xF1, 0xF8, 0xF2);

    // Abbreviated so 18 columns fit landscape A4 without dropping any of them.
    private static final String[] HEADERS = {
            "Order Date", "Invoice No.", "Order Ref.", "Customer", "Phone",
            "HSN", "Item", "Qty", "Unit Price", "Taxable Value",
            "CGST %", "CGST Amt", "SGST %", "SGST Amt", "Line Total",
            "Payment", "Pay Status", "Status",
    };

    private static final float[] COLUMN_WIDTHS = {
            2.0f, 1.6f, 1.5f, 2.1f, 1.5f,
            1.0f, 3.2f, 0.8f, 1.4f, 1.5f,
            0.9f, 1.4f, 0.9f, 1.4f, 1.5f,
            1.3f, 1.4f, 1.4f,
    };

    private final StoreDetails store;

    SalesReportPdfGenerator(StoreDetails store) {
        this.store = store;
    }

    byte[] render(List<OrderRepository.SalesReportRow> rows, LocalDate from, LocalDate to, String gstin) {
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 40, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(header(from, to, gstin));
            doc.add(rule());
            doc.add(table(rows));
            doc.add(footer());

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render sales report PDF", e);
        }
        return out.toByteArray();
    }

    /** Two-column masthead: store identity (left) + report title/date range (right). */
    private PdfPTable header(LocalDate from, LocalDate to, String gstin) {
        PdfPTable table = fullWidth(new float[] {3f, 2f});

        PdfPCell left = borderless();
        left.addElement(text(store.name(), font(18, Font.BOLD, BRAND)));
        left.addElement(text(store.address(), font(9, Font.NORMAL, MUTED)));
        left.addElement(text(store.contact(), font(9, Font.NORMAL, MUTED)));
        if (gstin != null && !gstin.isBlank()) {
            left.addElement(text("GSTIN: " + gstin, font(9, Font.NORMAL, MUTED)));
        }
        table.addCell(left);

        PdfPCell right = borderless();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.addElement(right(text("SALES REPORT — GST SUMMARY", font(16, Font.BOLD, INK))));
        right.addElement(right(text("From: " + DATE.format(from) + "   To: " + DATE.format(to),
                font(10, Font.NORMAL, MUTED))));
        table.addCell(right);
        return table;
    }

    /** Thin brand-coloured divider rule. */
    private PdfPTable rule() {
        PdfPTable table = fullWidth(new float[] {1f});
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(BRAND);
        cell.setBorderWidthBottom(1.5f);
        cell.setFixedHeight(10f);
        cell.setPaddingTop(6f);
        table.addCell(cell);
        table.setSpacingAfter(8f);
        return table;
    }

    private PdfPTable table(List<OrderRepository.SalesReportRow> rows) {
        PdfPTable table = fullWidth(COLUMN_WIDTHS);
        table.setHeaderRows(1);

        for (String h : HEADERS) {
            table.addCell(th(h));
        }

        long totalQty = 0;
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalLine = BigDecimal.ZERO;

        boolean zebra = false;
        for (OrderRepository.SalesReportRow row : rows) {
            Color bg = zebra ? ZEBRA : Color.WHITE;
            zebra = !zebra;
            BigDecimal legRate = legRate(row.getGstRate());

            table.addCell(td(DATE.format(row.getPlacedAt()), Element.ALIGN_LEFT, bg));
            table.addCell(td(nullToEmpty(row.getInvoiceNumber()), Element.ALIGN_LEFT, bg));
            table.addCell(td(nullToEmpty(row.getPublicCode()), Element.ALIGN_LEFT, bg));
            table.addCell(td(nullToEmpty(row.getCustomerName()), Element.ALIGN_LEFT, bg));
            table.addCell(td(nullToEmpty(row.getPhone()), Element.ALIGN_LEFT, bg));
            table.addCell(td(nullToEmpty(row.getHsnCode()), Element.ALIGN_CENTER, bg));
            table.addCell(td(description(row), Element.ALIGN_LEFT, bg));
            table.addCell(td(String.valueOf(row.getQty()), Element.ALIGN_CENTER, bg));
            table.addCell(td(money(row.getUnitPrice()), Element.ALIGN_RIGHT, bg));
            table.addCell(td(money(row.getTaxableValue()), Element.ALIGN_RIGHT, bg));
            table.addCell(td(percent(legRate), Element.ALIGN_CENTER, bg));
            table.addCell(td(money(row.getCgst()), Element.ALIGN_RIGHT, bg));
            table.addCell(td(percent(legRate), Element.ALIGN_CENTER, bg));
            table.addCell(td(money(row.getSgst()), Element.ALIGN_RIGHT, bg));
            table.addCell(td(money(row.getLineTotal()), Element.ALIGN_RIGHT, bg));
            table.addCell(td(nullToEmpty(row.getPaymentMethod()), Element.ALIGN_LEFT, bg));
            table.addCell(td(nullToEmpty(row.getPaymentStatus()), Element.ALIGN_LEFT, bg));
            table.addCell(td(nullToEmpty(row.getStatus()), Element.ALIGN_LEFT, bg));

            totalQty += row.getQty();
            totalTaxable = totalTaxable.add(nz(row.getTaxableValue()));
            totalCgst = totalCgst.add(nz(row.getCgst()));
            totalSgst = totalSgst.add(nz(row.getSgst()));
            totalLine = totalLine.add(nz(row.getLineTotal()));
        }

        // TOTALS row: text columns blank except "TOTAL" in Item Description.
        table.addCell(totalCell("", Element.ALIGN_LEFT));
        table.addCell(totalCell("", Element.ALIGN_LEFT));
        table.addCell(totalCell("", Element.ALIGN_LEFT));
        table.addCell(totalCell("", Element.ALIGN_LEFT));
        table.addCell(totalCell("", Element.ALIGN_LEFT));
        table.addCell(totalCell("", Element.ALIGN_CENTER));
        table.addCell(totalCell("TOTAL", Element.ALIGN_LEFT));
        table.addCell(totalCell(String.valueOf(totalQty), Element.ALIGN_CENTER));
        table.addCell(totalCell("", Element.ALIGN_RIGHT));
        table.addCell(totalCell(money(totalTaxable), Element.ALIGN_RIGHT));
        table.addCell(totalCell("", Element.ALIGN_CENTER));
        table.addCell(totalCell(money(totalCgst), Element.ALIGN_RIGHT));
        table.addCell(totalCell("", Element.ALIGN_CENTER));
        table.addCell(totalCell(money(totalSgst), Element.ALIGN_RIGHT));
        table.addCell(totalCell(money(totalLine), Element.ALIGN_RIGHT));
        table.addCell(totalCell("", Element.ALIGN_LEFT));
        table.addCell(totalCell("", Element.ALIGN_LEFT));
        table.addCell(totalCell("", Element.ALIGN_LEFT));

        table.setSpacingAfter(10f);
        return table;
    }

    private Paragraph footer() {
        Paragraph p = new Paragraph();
        p.add(new Phrase("All prices are inclusive of GST. No IGST applies — this store delivers intra-state only.",
                font(8, Font.NORMAL, MUTED)));
        p.setSpacingBefore(6f);
        return p;
    }

    // ---- row helpers --------------------------------------------------

    /** Item description: product name, plus " · " + label when the label is present. */
    private static String description(OrderRepository.SalesReportRow row) {
        String label = row.getLabel();
        String name = nullToEmpty(row.getProductName());
        return (label == null || label.isBlank()) ? name : name + " · " + label;
    }

    /** Per-leg (CGST or SGST) rate: the combined slab rate halved. */
    private static BigDecimal legRate(BigDecimal combinedRatePercent) {
        return combinedRatePercent == null
                ? null
                : combinedRatePercent.divide(TWO, 2, RoundingMode.HALF_UP);
    }

    private static String percent(BigDecimal rate) {
        return rate == null ? "—" : rate.stripTrailingZeros().toPlainString() + "%";
    }

    private static String money(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ---- low-level helpers (mirrors InvoicePdfGenerator) ---------------

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private static Paragraph text(String s, Font font) {
        Paragraph p = new Paragraph(s, font);
        p.setSpacingAfter(2f);
        return p;
    }

    private static Paragraph right(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static PdfPTable fullWidth(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPCell borderless() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    private static PdfPCell th(String label) {
        PdfPCell c = new PdfPCell(new Phrase(label, font(7.5f, Font.BOLD, Color.WHITE)));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBackgroundColor(BRAND);
        c.setPadding(4f);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static PdfPCell td(String value, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(value, font(7.5f, Font.NORMAL, INK)));
        c.setHorizontalAlignment(align);
        c.setBackgroundColor(bg);
        c.setPadding(4f);
        c.setBorderColor(LINE);
        c.setBorderWidth(0.4f);
        return c;
    }

    private static PdfPCell totalCell(String value, int align) {
        PdfPCell c = new PdfPCell(new Phrase(value, font(7.5f, Font.BOLD, INK)));
        c.setHorizontalAlignment(align);
        c.setPadding(4f);
        c.setBorder(Rectangle.TOP);
        c.setBorderColorTop(BRAND);
        c.setBorderWidthTop(1f);
        return c;
    }
}
