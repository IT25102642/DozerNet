package com.dozernet.module4_operator.web;

import com.dozernet.common.document.DocumentService;
import com.dozernet.common.document.DocumentType;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.module4_operator.dto.DriverRegisterForm;
import com.dozernet.module4_operator.service.OperatorService;
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
 * Public self-registration for independent drivers/operators.
 */
@Controller
public class DriverRegistrationController {

    private final OperatorService operatorService;
    private final DocumentService documentService;

    public DriverRegistrationController(OperatorService operatorService,
                                        DocumentService documentService) {
        this.operatorService = operatorService;
        this.documentService = documentService;
    }

    @GetMapping("/register/driver")
    public String showForm(Model model) {
        if (!model.containsAttribute("driverForm")) {
            model.addAttribute("driverForm", new DriverRegisterForm());
        }
        return "operator/driver-register";
    }

    @PostMapping("/register/driver")
    public String register(@Valid @ModelAttribute("driverForm") DriverRegisterForm form,
                           BindingResult binding,
                           @RequestParam(value = "licenceCopy", required = false) MultipartFile licenceCopy,
                           @RequestParam(value = "nicCopy", required = false) MultipartFile nicCopy) {
        if (binding.hasErrors()) {
            return "operator/driver-register";
        }
        try {
            var profile = operatorService.registerDriver(form);
            // Filed against the new account so the admin can check them before verifying.
            documentService.store(profile.getUser(), DocumentType.LICENCE, licenceCopy);
            documentService.store(profile.getUser(), DocumentType.NIC, nicCopy);
        } catch (BusinessRuleException ex) {
            binding.reject("registration", ex.getMessage());
            return "operator/driver-register";
        }
        return "redirect:/login?registered";
    }
}
