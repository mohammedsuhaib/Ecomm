package com.townbasket.orders.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Renders the sales-report rows into an .xlsx workbook using Apache POI.
 * Pure presentation, same split as {@link SalesReportPdfGenerator} for the
 * other format.
 */
@Component
class SalesReportXlsxGenerator {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(IST);
    private static final BigDecimal TWO = new BigDecimal("2");

    // Column order is the contract: GST audit reads this file by position.
    private static final String[] HEADERS = {
            "Order Date", "Invoice Number", "Order Reference", "Customer Name", "Customer Phone",
            "HSN Code", "Item Description", "Quantity", "Unit Price", "Taxable Value",
            "CGST Rate %", "CGST Amount", "SGST Rate %", "SGST Amount", "Line Total",
            "Payment Method", "Payment Status", "Order Status",
    };

    private static final int[] COLUMN_WIDTH_CHARS = {
            12, 16, 16, 22, 14, 10, 34, 9, 11, 13, 11, 12, 11, 12, 12, 15, 15, 15,
    };

    private static final int COL_ITEM_DESCRIPTION = 6;
    private static final int COL_QTY = 7;
    private static final int COL_UNIT_PRICE = 8;
    private static final int COL_TAXABLE_VALUE = 9;
    private static final int COL_CGST_RATE = 10;
    private static final int COL_CGST_AMOUNT = 11;
    private static final int COL_SGST_RATE = 12;
    private static final int COL_SGST_AMOUNT = 13;
    private static final int COL_LINE_TOTAL = 14;

    private final StoreDetails store;

    SalesReportXlsxGenerator(StoreDetails store) {
        this.store = store;
    }

    byte[] render(List<OrderRepository.SalesReportRow> rows, LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sales Report");

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            short moneyFormat = workbook.createDataFormat().getFormat("0.00");
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(moneyFormat);
            CellStyle moneyBoldStyle = workbook.createCellStyle();
            moneyBoldStyle.setDataFormat(moneyFormat);
            moneyBoldStyle.setFont(boldFont);
            short qtyFormat = workbook.createDataFormat().getFormat("0");
            CellStyle qtyStyle = workbook.createCellStyle();
            qtyStyle.setDataFormat(qtyFormat);
            CellStyle qtyBoldStyle = workbook.createCellStyle();
            qtyBoldStyle.setDataFormat(qtyFormat);
            qtyBoldStyle.setFont(boldFont);

            for (int c = 0; c < COLUMN_WIDTH_CHARS.length; c++) {
                sheet.setColumnWidth(c, COLUMN_WIDTH_CHARS[c] * 256);
            }

            int r = 0;
            r = header(sheet, titleStyle, boldStyle, from, to, r);
            r++; // blank separator row

            Row headerRow = sheet.createRow(r++);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            long totalQty = 0;
            BigDecimal totalTaxable = BigDecimal.ZERO;
            BigDecimal totalCgst = BigDecimal.ZERO;
            BigDecimal totalSgst = BigDecimal.ZERO;
            BigDecimal totalLine = BigDecimal.ZERO;

            for (OrderRepository.SalesReportRow row : rows) {
                Row xr = sheet.createRow(r++);
                xr.createCell(0).setCellValue(DATE.format(row.getPlacedAt()));
                xr.createCell(1).setCellValue(nullToEmpty(row.getInvoiceNumber()));
                xr.createCell(2).setCellValue(nullToEmpty(row.getPublicCode()));
                xr.createCell(3).setCellValue(nullToEmpty(row.getCustomerName()));
                xr.createCell(4).setCellValue(nullToEmpty(row.getPhone()));
                xr.createCell(5).setCellValue(nullToEmpty(row.getHsnCode()));
                xr.createCell(COL_ITEM_DESCRIPTION).setCellValue(description(row));

                Cell qtyCell = xr.createCell(COL_QTY);
                qtyCell.setCellValue(row.getQty());
                qtyCell.setCellStyle(qtyStyle);

                BigDecimal legRate = legRate(row.getGstRate());
                money(xr.createCell(COL_UNIT_PRICE), row.getUnitPrice(), moneyStyle);
                money(xr.createCell(COL_TAXABLE_VALUE), row.getTaxableValue(), moneyStyle);
                money(xr.createCell(COL_CGST_RATE), legRate, moneyStyle);
                money(xr.createCell(COL_CGST_AMOUNT), row.getCgst(), moneyStyle);
                money(xr.createCell(COL_SGST_RATE), legRate, moneyStyle);
                money(xr.createCell(COL_SGST_AMOUNT), row.getSgst(), moneyStyle);
                money(xr.createCell(COL_LINE_TOTAL), row.getLineTotal(), moneyStyle);

                xr.createCell(15).setCellValue(nullToEmpty(row.getPaymentMethod()));
                xr.createCell(16).setCellValue(nullToEmpty(row.getPaymentStatus()));
                xr.createCell(17).setCellValue(nullToEmpty(row.getStatus()));

                totalQty += row.getQty();
                totalTaxable = totalTaxable.add(nz(row.getTaxableValue()));
                totalCgst = totalCgst.add(nz(row.getCgst()));
                totalSgst = totalSgst.add(nz(row.getSgst()));
                totalLine = totalLine.add(nz(row.getLineTotal()));
            }

            Row totalsRow = sheet.createRow(r);
            for (int c = 0; c < HEADERS.length; c++) {
                totalsRow.createCell(c);
            }
            totalsRow.getCell(COL_ITEM_DESCRIPTION).setCellValue("TOTAL");
            totalsRow.getCell(COL_ITEM_DESCRIPTION).setCellStyle(boldStyle);
            Cell totalQtyCell = totalsRow.getCell(COL_QTY);
            totalQtyCell.setCellValue(totalQty);
            totalQtyCell.setCellStyle(qtyBoldStyle);
            money(totalsRow.getCell(COL_TAXABLE_VALUE), totalTaxable, moneyBoldStyle);
            money(totalsRow.getCell(COL_CGST_AMOUNT), totalCgst, moneyBoldStyle);
            money(totalsRow.getCell(COL_SGST_AMOUNT), totalSgst, moneyBoldStyle);
            money(totalsRow.getCell(COL_LINE_TOTAL), totalLine, moneyBoldStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render sales report xlsx", e);
        }
    }

    /** Store identity + report title + date range, above the column headers. */
    private int header(Sheet sheet, CellStyle titleStyle, CellStyle boldStyle, LocalDate from, LocalDate to, int r) {
        sheet.createRow(r++).createCell(0).setCellValue(store.name());
        sheet.getRow(r - 1).getCell(0).setCellStyle(titleStyle);
        sheet.createRow(r++).createCell(0).setCellValue(store.address());
        if (!store.gstin().isEmpty()) {
            sheet.createRow(r++).createCell(0).setCellValue("GSTIN: " + store.gstin());
        }
        r++; // blank
        Row titleRow = sheet.createRow(r++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Sales Report — GST Summary");
        titleCell.setCellStyle(boldStyle);
        Row rangeRow = sheet.createRow(r++);
        rangeRow.createCell(0).setCellValue("From: " + DATE.format(from) + "    To: " + DATE.format(to));
        return r;
    }

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

    private static void money(Cell cell, BigDecimal value, CellStyle style) {
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
