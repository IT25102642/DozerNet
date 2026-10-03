package com.dozernet.module6_payment.report;

import java.util.List;

/**
 * A finished report, ready to be rendered on screen or exported. Keeping every
 * report in one shape means the HTML view, the CSV writer and the PDF writer
 * are each written once rather than once per report.
 *
 * @param key      url-friendly identifier, e.g. "revenue-by-month"
 * @param title    heading shown on screen and in exports
 * @param subtitle short explanation of what the figures cover
 * @param columns  column headings
 * @param rows     data rows, already formatted for display
 * @param totals   optional totals row, aligned to {@code columns}; empty if none
 */
public record ReportTable(
        String key,
        String title,
        String subtitle,
        List<String> columns,
        List<List<String>> rows,
        List<String> totals
) {
    public boolean hasTotals() {
        return totals != null && !totals.isEmpty();
    }

    public boolean isEmpty() {
        return rows == null || rows.isEmpty();
    }
}
