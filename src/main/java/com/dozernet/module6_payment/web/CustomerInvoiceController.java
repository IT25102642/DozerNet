package com.dozernet.module6_payment.web;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import com.dozernet.module6_payment.entity.Invoice;
import com.dozernet.module6_payment.entity.InvoiceStatus;
import com.dozernet.module6_payment.service.CardPaymentRequest;
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
 * Customer-facing invoices: list, view, demo card payment portal, and thank-you.
 */
@Controller
public class CustomerInvoiceController {

    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public CustomerInvoiceController(PaymentService paymentService,
                                     CurrentUserService currentUserService) {
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/customer/invoices")
    public String list(Model model) {
        model.addAttribute("invoices",
                paymentService.invoicesForCustomer(currentUserService.require()));
        return "payment/customer-invoices";
    }

    @GetMapping("/customer/invoices/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Invoice invoice = requireOwnInvoice(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("payments", paymentService.paymentsFor(invoice));
        return "payment/customer-invoice-detail";
    }

    @GetMapping("/customer/invoices/{id}/pay")
    public String payForm(@PathVariable Long id, Model model) {
        Invoice invoice = requireOwnInvoice(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return "redirect:/customer/invoices/" + id + "/thank-you";
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessRuleException("This invoice was cancelled and cannot be paid.");
        }
        model.addAttribute("invoice", invoice);
        return "payment/customer-pay";
    }

    @PostMapping("/customer/invoices/{id}/pay")
    public String pay(@PathVariable Long id,
                      @RequestParam String cardholderName,
                      @RequestParam String cardNumber,
                      @RequestParam(required = false) String expiry,
                      @RequestParam(required = false) String cvc,
                      @RequestParam(required = false) BigDecimal amount,
                      RedirectAttributes ra) {
        try {
            CardPaymentRequest card = new CardPaymentRequest(cardholderName, cardNumber, expiry, cvc);
            paymentService.recordCustomerPayment(id, currentUserService.require(), card, amount);

            Invoice invoice = paymentService.getInvoice(id);
            if (invoice.getStatus() == InvoiceStatus.PAID) {
                return "redirect:/customer/invoices/" + id + "/thank-you";
            }
            // Part payment: back to the invoice, showing what is still owed.
            ra.addFlashAttribute("message", "Part payment received. Rs. " + invoice.getBalance()
                    + " is still outstanding on invoice " + invoice.getReference() + ".");
            return "redirect:/customer/invoices/" + id;
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/invoices/" + id + "/pay";
        }
    }

    @GetMapping("/customer/invoices/{id}/thank-you")
    public String thankYou(@PathVariable Long id, Model model) {
        Invoice invoice = requireOwnInvoice(id);
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            return "redirect:/customer/invoices/" + id;
        }
        model.addAttribute("invoice", invoice);
        model.addAttribute("payments", paymentService.paymentsFor(invoice));
        return "payment/customer-thank-you";
    }

    private Invoice requireOwnInvoice(Long id) {
        User customer = currentUserService.require();
        Invoice invoice = paymentService.getInvoice(id);
        if (!invoice.getCustomer().getId().equals(customer.getId())) {
            throw ResourceNotFoundException.of("Invoice", id);
        }
        return invoice;
    }
}
