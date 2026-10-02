package com.dozernet.common.user;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.model.Role;
import com.dozernet.common.validation.ValidationPatterns;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Shared account creation used by role-specific registration flows (owner,
 * operator). Centralises uniqueness checks, phone normalisation and password
 * hashing so every module registers users the same way.
 *
 * <p>Because one person may hold several roles, signing up for a new role with
 * an email that already exists adds the role to that account (after verifying
 * the password) instead of rejecting the registration.</p>
 */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createAccount(String fullName, String email, String phone,
                              String identityCardNumber,
                              String rawPassword, String confirmPassword,
                              Role role, boolean verified) {
        String normalisedEmail = email.trim().toLowerCase();
        String normalisedPhone = normalisePhone(phone);
        String nic = normaliseNic(identityCardNumber);

        if (rawPassword == null || !rawPassword.equals(confirmPassword)) {
            throw new BusinessRuleException("Passwords do not match");
        }
        requireStrongPassword(rawPassword);

        Optional<User> existing = userRepository.findByEmail(normalisedEmail);
        if (existing.isPresent()) {
            return addRoleToExistingAccount(existing.get(), rawPassword, role, verified);
        }

        if (userRepository.existsByPhone(normalisedPhone)) {
            throw new BusinessRuleException("An account with this phone number already exists");
        }
        if (userRepository.existsByIdentityCardNumber(nic)) {
            throw new BusinessRuleException("An account with this identity card number already exists");
        }

        User user = new User(fullName.trim(), normalisedEmail, normalisedPhone, nic,
                passwordEncoder.encode(rawPassword), role);
        user.setVerified(verified);
        return userRepository.save(user);
    }

    /**
     * Adds a second role to an account that already exists. The person must
     * prove ownership by entering their existing password, which stops someone
     * from claiming another user's email through a registration form.
     */
    private User addRoleToExistingAccount(User user, String rawPassword, Role role, boolean verified) {
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessRuleException(
                    "An account with this email already exists. Enter that account's password to add this role.");
        }
        if (user.hasRole(role)) {
            throw new BusinessRuleException("This account is already registered as a "
                    + role.getDisplayName().toLowerCase());
        }
        user.addRole(role);
        // A newly added Owner/Operator role still needs admin verification.
        if (!verified) {
            user.setVerified(false);
        }
        return userRepository.save(user);
    }

    /** Q25 password policy: 8+ characters with an uppercase letter and a number. */
    public static void requireStrongPassword(String rawPassword) {
        if (rawPassword == null || !rawPassword.matches(ValidationPatterns.PASSWORD)) {
            throw new BusinessRuleException(ValidationPatterns.PASSWORD_MSG);
        }
    }

    /** Canonical NIC form: trim + uppercase letter suffix (V/X). */
    public static String normaliseNic(String raw) {
        return raw == null ? null : raw.trim().toUpperCase();
    }

    public static String normalisePhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("94") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }
}
