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
import com.townbasket.orders.InvoiceService;
import com.townbasket.orders.OrderDto;
import com.townbasket.orders.OrderItemDto;
import com.townbasket.shared.BusinessRuleException;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Renders an {@link OrderDto} into a one-page PDF invoice using OpenPDF.
 *
 * <p>Pure presentation: it reads only fields already exposed on the public
 * {@link OrderDto} (never the internal COGS), so it adds no new data coupling.
 * Store identity is config-driven (sensible single-store defaults) so the same
 * image works locally and in prod.
 */
@Component
class InvoicePdfGenerator implements InvoiceService {

    private static final Locale IN = new Locale("en", "IN");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(IST);

    // Brand palette (mirrors the admin/storefront marigold theme).
    private static final Color BRAND = new Color(0xB4, 0x53, 0x09);   // deep amber
    private static final Color INK = new Color(0x1A, 0x1A, 0x1A);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color LINE = new Color(0xE2, 0xE2, 0xE2);
    private static final Color ZEBRA = new Color(0xFB, 0xF7, 0xEE);

    private final String storeName;
    private final String storeAddress;
    private final String storeContact;

    InvoicePdfGenerator(
            @Value("${townbasket.invoice.store-name:Town Basket}") String storeName,
            @Value("${townbasket.invoice.store-address:Mysuru, Karnataka, India}") String storeAddress,
            @Value("${townbasket.invoice.store-contact:town-basket.com}") String storeContact) {
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.storeContact = storeContact;
    }

    @Override
    public byte[] renderInvoicePdf(OrderDto order) {
        if (order == null) {
            throw new BusinessRuleException("Cannot render an invoice for a missing order.");
        }
        // Indian digit grouping (e.g. 1,00,000.00); NumberFormat isn't thread-safe,
        // so build one per render (invoices are small, so the cost is negligible).
        NumberFormat money = NumberFormat.getNumberInstance(IN);
        money.setMinimumFractionDigits(2);
        money.setMaximumFractionDigits(2);

        Document doc = new Document(PageSize.A4, 42, 42, 48, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(header(order));
            doc.add(rule());
            doc.add(billTo(order));
            doc.add(itemsTable(order, money));
            doc.add(totals(order, money));
            doc.add(footer(order));

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render invoice PDF for order " + order.id(), e);
        }
        return out.toByteArray();
    }

    // ---- sections ----------------------------------------------------------

    /** Two-column masthead: store identity (left) + invoice meta (right). */
    private PdfPTable header(OrderDto order) {
        PdfPTable table = fullWidth(new float[] {3f, 2f});

        PdfPCell left = borderless();
        left.addElement(text(storeName, font(20, Font.BOLD, BRAND)));
        left.addElement(text(storeAddress, font(9, Font.NORMAL, MUTED)));
        left.addElement(text(storeContact, font(9, Font.NORMAL, MUTED)));
        table.addCell(left);

        PdfPCell right = borderless();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.addElement(right(text("INVOICE", font(22, Font.BOLD, INK))));
        right.addElement(right(text("Invoice #INV-" + order.id(), font(10, Font.NORMAL, MUTED))));
        right.addElement(right(text("Date: " + DATE.format(order.placedAt()), font(10, Font.NORMAL, MUTED))));
        right.addElement(right(text("Status: " + order.status(), font(10, Font.NORMAL, MUTED))));
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
        cell.setPaddingTop(8f);
        table.addCell(cell);
        table.setSpacingAfter(10f);
        return table;
    }

    /** "Bill to" block — customer name, phone, delivery address. */
    private PdfPTable billTo(OrderDto order) {
        PdfPTable table = fullWidth(new float[] {1f});
        PdfPCell cell = borderless();
        cell.addElement(text("BILL TO", font(8, Font.BOLD, MUTED)));
        cell.addElement(text(order.customerName(), font(12, Font.BOLD, INK)));
        cell.addElement(text(order.phone(), font(10, Font.NORMAL, INK)));
        if (order.address() != null && order.address().line() != null) {
            cell.addElement(text(order.address().line(), font(10, Font.NORMAL, MUTED)));
        }
        table.addCell(cell);
        table.setSpacingAfter(14f);
        return table;
    }

    /** Itemised line table: Item | Qty | Unit Price | Amount. */
    private PdfPTable itemsTable(OrderDto order, NumberFormat money) {
        PdfPTable table = fullWidth(new float[] {5f, 1.2f, 2f, 2f});

        table.addCell(th("Item", Element.ALIGN_LEFT));
        table.addCell(th("Qty", Element.ALIGN_CENTER));
        table.addCell(th("Unit Price", Element.ALIGN_RIGHT));
        table.addCell(th("Amount", Element.ALIGN_RIGHT));

        boolean zebra = false;
        for (OrderItemDto item : order.items()) {
            Color bg = zebra ? ZEBRA : Color.WHITE;
            zebra = !zebra;
            String name = item.label() == null || item.label().isBlank()
                    ? item.productName()
                    : item.productName() + "  (" + item.label() + ")";
            table.addCell(td(name, Element.ALIGN_LEFT, bg));
            table.addCell(td(String.valueOf(item.qty()), Element.ALIGN_CENTER, bg));
            table.addCell(td(money(item.unitPrice(), money), Element.ALIGN_RIGHT, bg));
            table.addCell(td(money(item.lineTotal(), money), Element.ALIGN_RIGHT, bg));
        }
        table.setSpacingAfter(12f);
        return table;
    }

    /** Right-aligned subtotal/total summary + payment line. */
    private PdfPTable totals(OrderDto order, NumberFormat money) {
        PdfPTable wrap = fullWidth(new float[] {3f, 2f});
        wrap.addCell(borderless()); // spacer to push the summary right

        PdfPTable sum = new PdfPTable(new float[] {1.4f, 1f});
        sum.setWidthPercentage(100);
        sum.addCell(sumLabel("Subtotal", false));
        sum.addCell(sumValue(money(order.subtotal(), money), false));
        sum.addCell(sumLabel("Total", true));
        sum.addCell(sumValue(money(order.total(), money), true));

        String pay = ("COD".equalsIgnoreCase(order.paymentMethod()) ? "Cash on Delivery" : order.paymentMethod())
                + " — " + order.paymentStatus();
        PdfPCell payCell = sumLabel("Payment: " + pay, false);
        payCell.setColspan(2);
        payCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sum.addCell(payCell);

        PdfPCell sumHolder = borderless();
        sumHolder.addElement(sum);
        wrap.addCell(sumHolder);
        wrap.setSpacingAfter(18f);
        return wrap;
    }

    /** Footer note. */
    private Paragraph footer(OrderDto order) {
        Paragraph p = new Paragraph();
        p.add(new Phrase("Thank you for shopping with " + storeName + "!\n", font(10, Font.BOLD, BRAND)));
        p.add(new Phrase("This is a computer-generated invoice and does not require a signature.",
                font(8, Font.NORMAL, MUTED)));
        p.setSpacingBefore(6f);
        return p;
    }

    // ---- low-level helpers -------------------------------------------------

    private static String money(BigDecimal value, NumberFormat fmt) {
        return "Rs " + fmt.format(value == null ? BigDecimal.ZERO : value);
    }

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

    /** Table header cell. */
    private static PdfPCell th(String label, int align) {
        PdfPCell c = new PdfPCell(new Phrase(label, font(9, Font.BOLD, Color.WHITE)));
        c.setHorizontalAlignment(align);
        c.setBackgroundColor(BRAND);
        c.setPadding(6f);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    /** Table body cell. */
    private static PdfPCell td(String value, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(value, font(10, Font.NORMAL, INK)));
        c.setHorizontalAlignment(align);
        c.setBackgroundColor(bg);
        c.setPadding(6f);
        c.setBorderColor(LINE);
        c.setBorderWidth(0.5f);
        return c;
    }

    private static PdfPCell sumLabel(String label, boolean bold) {
        PdfPCell c = new PdfPCell(new Phrase(label, font(bold ? 12 : 10, bold ? Font.BOLD : Font.NORMAL, INK)));
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setBorder(bold ? Rectangle.TOP : Rectangle.NO_BORDER);
        c.setBorderColorTop(LINE);
        c.setPadding(5f);
        return c;
    }

    private static PdfPCell sumValue(String value, boolean bold) {
        PdfPCell c = new PdfPCell(new Phrase(value, font(bold ? 12 : 10, bold ? Font.BOLD : Font.NORMAL,
                bold ? BRAND : INK)));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setBorder(bold ? Rectangle.TOP : Rectangle.NO_BORDER);
        c.setBorderColorTop(LINE);
        c.setPadding(5f);
        return c;
    }
}
