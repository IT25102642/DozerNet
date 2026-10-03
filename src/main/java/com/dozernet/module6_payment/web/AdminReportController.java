package com.dozernet.module6_payment.web;

import com.dozernet.module6_payment.report.ReportExporter;
import com.dozernet.module6_payment.report.ReportService;
import com.dozernet.module6_payment.report.ReportTable;
import com.dozernet.module6_payment.service.AdminService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;

/**
 * Booking / revenue reporting for the administrator. Every report can be read
 * on screen or downloaded as CSV or PDF.
 */
@Controller
public class AdminReportController {

    private final AdminService adminService;
    private final ReportService reportService;
    private final ReportExporter reportExporter;

    public AdminReportController(AdminService adminService,
                                 ReportService reportService,
                                 ReportExporter reportExporter) {
        this.adminService = adminService;
        this.reportService = reportService;
        this.reportExporter = reportExporter;
    }

    @GetMapping("/admin/reports")
    public String reports(Model model) {
        model.addAttribute("report", adminService.revenueReport());
        model.addAttribute("tables", reportService.allReports());
        return "payment/admin-reports";
    }

    @GetMapping("/admin/reports/{key}.csv")
    public ResponseEntity<byte[]> csv(@PathVariable String key) {
        ReportTable report = reportService.byKey(key);
        return download(reportExporter.toCsv(report), key, "csv", MediaType.parseMediaType("text/csv"));
    }

    @GetMapping("/admin/reports/{key}.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String key) {
        ReportTable report = reportService.byKey(key);
        return download(reportExporter.toPdf(report), key, "pdf", MediaType.APPLICATION_PDF);
    }

    private ResponseEntity<byte[]> download(byte[] body, String key, String extension, MediaType type) {
        String filename = "dozernet-" + key + "-" + LocalDate.now() + "." + extension;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
