package com.dozernet.module6_payment;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.model.Role;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module6_payment.dto.StaffAccountForm;
import com.dozernet.module6_payment.service.StaffAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAccountServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;

    @InjectMocks StaffAccountService service;

    private static StaffAccountForm form() {
        StaffAccountForm form = new StaffAccountForm();
        form.setFullName("  Nimali Silva ");
        form.setEmail("  Nimali@DozerNet.lk ");
        form.setPhone("077 123 4567");
        form.setIdentityCardNumber("199512345678");
        form.setPassword("Secret123");
        form.setConfirmPassword("Secret123");
        return form;
    }

    @Test
    void createsAVerifiedEnabledAdministratorWithAHashedPassword() {
        when(passwordEncoder.encode("Secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User admin = service.createAdmin(form());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(admin);
        assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
        assertThat(admin.getFullName()).isEqualTo("Nimali Silva");
        assertThat(admin.getEmail()).isEqualTo("nimali@dozernet.lk");
        assertThat(admin.getPhone()).isEqualTo("0771234567");
        assertThat(admin.getPasswordHash()).isEqualTo("hashed");
        assertThat(admin.isVerified()).isTrue();
        assertThat(admin.isEnabled()).isTrue();
        verify(auditService).record(eq("ADMIN_CREATED"), eq("User"), any(), anyString());
    }

    @Test
    void mismatchedPasswordsAreRefused() {
        StaffAccountForm form = form();
        form.setConfirmPassword("Different123");

        assertThatThrownBy(() -> service.createAdmin(form))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("do not match");
        verify(userRepository, never()).save(any());
    }

    @Test
    void weakPasswordsAreRefused() {
        StaffAccountForm form = form();
        form.setPassword("weak");
        form.setConfirmPassword("weak");

        assertThatThrownBy(() -> service.createAdmin(form)).isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void anExistingEmailIsRefusedAndNeverGainsTheAdminRole() {
        when(userRepository.existsByEmail("nimali@dozernet.lk")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmin(form()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("email already exists");
        verify(userRepository, never()).save(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    void anExistingPhoneIsRefused() {
        when(userRepository.existsByPhone("0771234567")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmin(form()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("phone number");
        verify(userRepository, never()).save(any());
    }

    @Test
    void anExistingNicIsRefused() {
        when(userRepository.existsByIdentityCardNumber("199512345678")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmin(form()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("identity card");
        verify(userRepository, never()).save(any());
    }
}
