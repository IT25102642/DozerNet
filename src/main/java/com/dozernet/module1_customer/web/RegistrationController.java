package com.dozernet.module1_customer.web;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.module1_customer.dto.RegisterForm;
import com.dozernet.module1_customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public customer self-registration (Customer Management module).
 */
@Controller
public class RegistrationController {

    private final CustomerService customerService;

    public RegistrationController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/register")
    public String showForm(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "customer/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                           BindingResult binding,
                           @RequestParam(value = "nicCopy", required = false) MultipartFile nicCopy) {
        if (binding.hasErrors()) {
            return "customer/register";
        }
        try {
            customerService.register(form, nicCopy);
        } catch (BusinessRuleException ex) {
            binding.reject("registration", ex.getMessage());
            return "customer/register";
        }
        return "redirect:/login?registered";
    }
}
