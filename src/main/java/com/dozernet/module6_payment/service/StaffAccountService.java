package com.dozernet.module6_payment.service;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.model.Role;
import com.dozernet.common.user.AccountService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module6_payment.dto.StaffAccountForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates staff (administrator) accounts from inside the admin dashboard.
 *
 * <p>Unlike public registration, this never attaches the role to an existing
 * account: if the email, phone or NIC is already in use it refuses, so granting
 * admin rights is always an explicit, audited act that can't be triggered by
 * knowing someone's password.</p>
 */
@Service
public class StaffAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public StaffAccountService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public User createAdmin(StaffAccountForm form) {
        String email = form.getEmail().trim().toLowerCase();
        String phone = AccountService.normalisePhone(form.getPhone());
        String nic = AccountService.normaliseNic(form.getIdentityCardNumber());

        if (form.getPassword() == null || !form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessRuleException("Passwords do not match");
        }
        AccountService.requireStrongPassword(form.getPassword());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessRuleException("An account with this phone number already exists.");
        }
        if (userRepository.existsByIdentityCardNumber(nic)) {
            throw new BusinessRuleException("An account with this identity card number already exists.");
        }

        User admin = new User(form.getFullName().trim(), email, phone, nic,
                passwordEncoder.encode(form.getPassword()), Role.ADMIN);
        admin.setVerified(true);
        User saved = userRepository.save(admin);

        auditService.record("ADMIN_CREATED", "User", saved.getId(), "New staff administrator account created");
        return saved;
    }
}
