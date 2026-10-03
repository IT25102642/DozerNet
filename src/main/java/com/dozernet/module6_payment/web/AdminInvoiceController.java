package com.dozernet.module6_payment.web;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.module6_payment.entity.PaymentMethod;
import com.dozernet.module6_payment.service.PaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/**
 * Admin invoicing & payments: generate invoices for completed jobs and record
 * payments against them.
 */
@Controller
public class AdminInvoiceController {

    private final PaymentService paymentService;

    public AdminInvoiceController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/admin/invoices")
    public String list(Model model) {
        model.addAttribute("invoices", paymentService.allInvoices());
        model.addAttribute("pendingInvoices", paymentService.bookingsWithoutInvoice());
        return "payment/admin-invoices";
    }

    @PostMapping("/admin/invoices/generate/{bookingId}")
    public String generate(@PathVariable Long bookingId, RedirectAttributes ra) {
        try {
            paymentService.generateInvoice(bookingId);
            ra.addFlashAttribute("success", "Invoice generated.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/invoices";
    }

    @PostMapping("/admin/invoices/generate-all")
    public String generateAll(RedirectAttributes ra) {
        int n = paymentService.generateForAllCompleted();
        ra.addFlashAttribute("success", n + " invoice(s) generated.");
        return "redirect:/admin/invoices";
    }

    /** Chases every customer who still owes money on an invoice. */
    @PostMapping("/admin/invoices/remind")
    public String remindOutstanding(RedirectAttributes ra) {
        int reminded = paymentService.remindAllOutstanding();
        ra.addFlashAttribute("success", reminded == 0
                ? "Nothing outstanding - no reminders needed."
                : "Balance reminders sent for " + reminded + " invoice(s).");
        return "redirect:/admin/invoices";
    }

    @GetMapping("/admin/invoices/{id}")
    public String detail(@PathVariable Long id, Model model) {
        var invoice = paymentService.getInvoice(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("payments", paymentService.paymentsFor(invoice));
        // A refund is written by the cancellation flow, not entered by hand.
        model.addAttribute("methods", java.util.Arrays.stream(PaymentMethod.values())
                .filter(m -> m != PaymentMethod.REFUND)
                .toList());
        return "payment/admin-invoice-detail";
    }

    @PostMapping("/admin/invoices/{id}/pay")
    public String pay(@PathVariable Long id,
                      @RequestParam BigDecimal amount,
                      @RequestParam PaymentMethod method,
                      @RequestParam(required = false) String reference,
                      RedirectAttributes ra) {
        try {
            paymentService.recordPayment(id, amount, method, reference);
            ra.addFlashAttribute("success", "Payment recorded.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/invoices/" + id;
    }
}
