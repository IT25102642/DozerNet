package com.dozernet.module1_customer.service;

import com.dozernet.common.document.Document;
import com.dozernet.common.document.DocumentService;
import com.dozernet.common.document.DocumentType;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.model.Role;
import com.dozernet.common.user.AccountService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module1_customer.dto.ProfileForm;
import com.dozernet.module1_customer.dto.RegisterForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Business logic for customer accounts: registration, profile updates and
 * password changes. All rules that Bean Validation cannot express (uniqueness,
 * password confirmation) are enforced here.
 */
@Service
public class CustomerService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DocumentService documentService;

    public CustomerService(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           DocumentService documentService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.documentService = documentService;
    }

    @Transactional
    public User register(RegisterForm form) {
        return register(form, null);
    }

    /**
     * Registers a customer and, when supplied, files their NIC copy against the
     * new account so it can be reused for a later owner or driver registration.
     */
    @Transactional
    public User register(RegisterForm form, MultipartFile nicCopy) {
        String email = form.getEmail().trim().toLowerCase();
        String phone = AccountService.normalisePhone(form.getPhone());
        String nic = AccountService.normaliseNic(form.getIdentityCardNumber());

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessRuleException("Passwords do not match");
        }
        AccountService.requireStrongPassword(form.getPassword());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessRuleException("An account with this phone number already exists");
        }
        if (userRepository.existsByIdentityCardNumber(nic)) {
            throw new BusinessRuleException("An account with this identity card number already exists");
        }

        User user = new User(
                form.getFullName().trim(),
                email,
                phone,
                nic,
                passwordEncoder.encode(form.getPassword()),
                Role.CUSTOMER);
        user.setVerified(true);
        User saved = userRepository.save(user);

        documentService.store(saved, DocumentType.NIC, nicCopy);
        return saved;
    }

    @Transactional
    public void updateProfile(User user, ProfileForm form) {
        String phone = AccountService.normalisePhone(form.getPhone());
        if (!phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new BusinessRuleException("That phone number is already in use");
        }
        user.setFullName(form.getFullName().trim());
        user.setPhone(phone);
        // identityCardNumber is immutable after registration
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword, String confirm) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        AccountService.requireStrongPassword(newPassword);
        if (!newPassword.equals(confirm)) {
            throw new BusinessRuleException("New passwords do not match");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /** Documents this customer already has on file, for reuse and admin review. */
    public List<Document> documentsFor(User user) {
        return documentService.forOwner(user);
    }

    /** Uploads an extra document (e.g. a replacement NIC copy) to the account. */
    @Transactional
    public Document uploadDocument(User user, DocumentType type, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose a file to upload.");
        }
        return documentService.store(user, type, file);
    }

    /**
     * Clears the "we could not reach you" flag once the customer has corrected
     * their contact details.
     */
    @Transactional
    public void clearContactWarning(User user) {
        if (user.isContactUnreachable()) {
            user.setContactUnreachable(false);
            userRepository.save(user);
        }
    }
}
