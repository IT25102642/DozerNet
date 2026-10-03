package com.dozernet.module1_customer;

import com.dozernet.common.document.DocumentService;
import com.dozernet.common.model.Role;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module1_customer.dto.ProfileForm;
import com.dozernet.module1_customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock DocumentService documentService;

    @InjectMocks CustomerService customerService;

    private static User customer() {
        User user = new User("Test Customer", "test@example.com", "0771234567", "hash", Role.CUSTOMER);
        user.setContactUnreachable(true);
        return user;
    }

    private static ProfileForm form(String name, String phone) {
        ProfileForm form = new ProfileForm();
        form.setFullName(name);
        form.setPhone(phone);
        return form;
    }

    @Test
    void savingTheProfileClearsTheUnreachableWarning() {
        User user = customer();

        customerService.updateProfile(user, form("Test Customer", "0771234567"));

        assertThat(user.isContactUnreachable()).isFalse();
    }

    @Test
    void clearContactWarningIsHarmlessWhenNothingWasFlagged() {
        User user = customer();
        user.setContactUnreachable(false);

        customerService.clearContactWarning(user);

        assertThat(user.isContactUnreachable()).isFalse();
    }
}
