package com.townbasket.inventory.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Builds the inventory valuation {@code .xlsx} workbook: one sheet, a bold
 * header row, plain data rows (one per stock row, already sorted by product
 * name then variant label by the caller's query) and a bold GRAND TOTAL row.
 *
 * <p>Pure Apache POI workbook-building — no persistence, no HTTP. Kept out of
 * both the {@code @RestController} and the public service interface so the
 * POI dependency stays an inventory-internal implementation detail.
 */
final class InventoryValuationXlsxGenerator {

    private static final String[] HEADERS = {
            "Product Name", "Variant Label", "On Hand Quantity", "Cost Price", "Total Value"
    };
    private static final String CURRENCY_FORMAT = "#,##0.00";

    private InventoryValuationXlsxGenerator() {
    }

    static byte[] generate(List<InventoryValuationRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventory Valuation");

            CellStyle boldStyle = boldStyle(workbook);
            CellStyle currencyStyle = currencyStyle(workbook, false);
            CellStyle boldCurrencyStyle = currencyStyle(workbook, true);

            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(boldStyle);
            }

            BigDecimal grandTotal = BigDecimal.ZERO;
            for (InventoryValuationRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.productName());
                row.createCell(1).setCellValue(r.variantLabel());
                row.createCell(2).setCellValue(r.onHand());

                Cell costCell = row.createCell(3);
                costCell.setCellValue(r.costPrice().doubleValue());
                costCell.setCellStyle(currencyStyle);

                BigDecimal totalValue = r.totalValue();
                Cell totalCell = row.createCell(4);
                totalCell.setCellValue(totalValue.doubleValue());
                totalCell.setCellStyle(currencyStyle);

                grandTotal = grandTotal.add(totalValue);
            }

            Row totalRow = sheet.createRow(rowIdx);
            Cell labelCell = totalRow.createCell(0);
            labelCell.setCellValue("GRAND TOTAL");
            labelCell.setCellStyle(boldStyle);
            // Variant Label / On Hand Quantity / Cost Price stay blank on the total row.
            totalRow.createCell(1).setCellStyle(boldStyle);
            totalRow.createCell(2).setCellStyle(boldStyle);
            totalRow.createCell(3).setCellStyle(boldStyle);
            Cell grandTotalCell = totalRow.createCell(4);
            grandTotalCell.setCellValue(grandTotal.doubleValue());
            grandTotalCell.setCellStyle(boldCurrencyStyle);

            // Fixed, generous widths (POI's autoSizeColumn needs AWT font metrics
            // that aren't reliably available in a headless server JVM).
            sheet.setColumnWidth(0, 40 * 256);
            sheet.setColumnWidth(1, 24 * 256);
            sheet.setColumnWidth(2, 18 * 256);
            sheet.setColumnWidth(3, 16 * 256);
            sheet.setColumnWidth(4, 18 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build inventory valuation workbook", e);
        }
    }

    private static CellStyle boldStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static CellStyle currencyStyle(Workbook workbook, boolean bold) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(CURRENCY_FORMAT));
        if (bold) {
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        return style;
    }
}
