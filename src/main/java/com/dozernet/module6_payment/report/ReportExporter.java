package com.dozernet.module6_payment.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link ReportTable} into a downloadable file. CSV is written by hand
 * (the format is small enough that a library would add more weight than value);
 * PDF is drawn with Apache PDFBox in landscape so wide reports still fit.
 */
@Component
public class ReportExporter {

    private static final float MARGIN = 36f;
    private static final float FONT_SIZE = 9f;
    private static final float HEADER_FONT_SIZE = 10f;
    private static final float LINE_HEIGHT = 14f;

    // ---------- CSV ----------

    public byte[] toCsv(ReportTable report) {
        StringBuilder csv = new StringBuilder();
        csv.append(escapeRow(List.of(report.title())));
        csv.append(escapeRow(List.of(report.subtitle())));
        csv.append(escapeRow(List.of("Generated: " + LocalDate.now())));
        csv.append('\n');
        csv.append(escapeRow(report.columns()));
        for (List<String> row : report.rows()) {
            csv.append(escapeRow(row));
        }
        if (report.hasTotals()) {
            csv.append(escapeRow(report.totals()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escapeRow(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escapeCell(cells.get(i)));
        }
        return line.append('\n').toString();
    }

    /** Quotes a cell when it contains a comma, quote or newline, per RFC 4180. */
    private static String escapeCell(String value) {
        String cell = value == null ? "" : value;
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            return '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }

    // ---------- PDF ----------

    public byte[] toPdf(ReportTable report) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            List<List<String>> body = new ArrayList<>(report.rows());
            if (report.hasTotals()) {
                body.add(report.totals());
            }

            float usableWidth = pageSize.getWidth() - (2 * MARGIN);
            float[] columnWidths = columnWidths(report, usableWidth);

            int rowIndex = 0;
            boolean firstPage = true;
            do {
                PDPage page = new PDPage(pageSize);
                document.addPage(page);

                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float y = pageSize.getHeight() - MARGIN;

                    if (firstPage) {
                        y = writeLine(content, report.title(), MARGIN, y, 16f, true);
                        y = writeLine(content, report.subtitle(), MARGIN, y - 2, FONT_SIZE, false);
                        y = writeLine(content, "DozerNet — generated " + LocalDate.now(),
                                MARGIN, y, FONT_SIZE, false);
                        y -= 8;
                        firstPage = false;
                    }

                    y = writeRow(content, report.columns(), columnWidths, y, true);
                    while (rowIndex < body.size() && y > MARGIN + LINE_HEIGHT) {
                        boolean isTotals = report.hasTotals() && rowIndex == body.size() - 1;
                        y = writeRow(content, body.get(rowIndex), columnWidths, y, isTotals);
                        rowIndex++;
                    }

                    if (body.isEmpty()) {
                        writeLine(content, "No data for this report yet.", MARGIN, y - 4, FONT_SIZE, false);
                    }
                }
            } while (rowIndex < body.size());

            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not build the PDF report", ex);
        }
    }

    /** Splits the page width in proportion to the longest text in each column. */
    private static float[] columnWidths(ReportTable report, float usableWidth) {
        int columnCount = report.columns().size();
        float[] weights = new float[columnCount];
        float totalWeight = 0;

        for (int c = 0; c < columnCount; c++) {
            int longest = report.columns().get(c).length();
            for (List<String> row : report.rows()) {
                if (c < row.size()) {
                    longest = Math.max(longest, safe(row.get(c)).length());
                }
            }
            weights[c] = Math.min(longest, 40);
            totalWeight += weights[c];
        }

        float[] widths = new float[columnCount];
        for (int c = 0; c < columnCount; c++) {
            widths[c] = totalWeight == 0 ? usableWidth / columnCount : usableWidth * (weights[c] / totalWeight);
        }
        return widths;
    }

    private static float writeRow(PDPageContentStream content, List<String> cells,
                                  float[] widths, float y, boolean bold) throws IOException {
        float x = MARGIN;
        for (int c = 0; c < widths.length; c++) {
            String cell = c < cells.size() ? safe(cells.get(c)) : "";
            writeLine(content, fit(cell, widths[c]), x, y, bold ? HEADER_FONT_SIZE : FONT_SIZE, bold);
            x += widths[c];
        }
        return y - LINE_HEIGHT;
    }

    private static float writeLine(PDPageContentStream content, String text, float x, float y,
                                   float size, boolean bold) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(bold
                ? Standard14Fonts.FontName.HELVETICA_BOLD
                : Standard14Fonts.FontName.HELVETICA), size);
        content.newLineAtOffset(x, y);
        content.showText(sanitise(text));
        content.endText();
        return y - LINE_HEIGHT;
    }

    /** Trims a cell to roughly the column width so columns never run into each other. */
    private static String fit(String text, float width) {
        int maxChars = Math.max((int) (width / (FONT_SIZE * 0.52f)) - 1, 3);
        return text.length() <= maxChars ? text : text.substring(0, maxChars - 1) + "…";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Standard-14 fonts only cover WinAnsi, so drop anything they cannot draw. */
    private static String sanitise(String text) {
        return text.replace('—', '-')
                .replace('–', '-')
                .replace('…', '.')
                .replaceAll("[^\\x20-\\xFF]", "");
    }
}
