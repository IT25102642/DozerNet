package com.dozernet;

import com.dozernet.common.model.Role;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.repository.BookingRepository;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.repository.MachineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staff-admin creation and customer self-deletion, driven through the real HTTP
 * endpoints and the real login on the H2 profile. Uses its own uniquely named
 * accounts and cleans up the bookings it makes, so it can share the database
 * with the ordered end-to-end flow test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AccountManagementIntegrationTest {

    private static final String ADMIN = "admin@dozernet.lk";
    private static final String PASSWORD = "Secret123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired MachineRepository machineRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingService bookingService;
    @Autowired PasswordEncoder passwordEncoder;

    /** Customers this class creates; their bookings are removed afterwards so the shared DB stays clean. */
    private final List<Long> createdCustomerIds = new ArrayList<>();

    @AfterEach
    void removeBookingsMadeByThisTest() {
        for (Long id : createdCustomerIds) {
            userRepository.findById(id).ifPresent(u ->
                    bookingRepository.deleteAll(bookingRepository.findByCustomerOrderByStartDateDesc(u)));
        }
        createdCustomerIds.clear();
    }

    // ---------- creating staff admins ----------

    @Test
    void anAdminCreatesAnotherAdminWhoCanThenSignInAndUseTheDashboard() throws Exception {
        mvc.perform(post("/admin/users/new").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("fullName", "Staff Admin One")
                        .param("email", "Staff.One@Example.com")
                        .param("phone", "0714440001")
                        .param("identityCardNumber", "199001010101")
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("success"));

        User created = userRepository.findByEmail("staff.one@example.com").orElseThrow();
        assertThat(created.getRoles()).containsExactly(Role.ADMIN);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.isVerified()).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo(PASSWORD);

        // The brand-new account signs in with the real login form...
        mvc.perform(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders
                        .formLogin("/login").user("email", "staff.one@example.com").password("password", PASSWORD))
                .andExpect(authenticated().withRoles("ADMIN"));
        // ...and reaches the admin dashboard.
        mvc.perform(get("/admin/dashboard").with(user("staff.one@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void creatingAnAdminWithAnExistingEmailIsRefusedAndChangesNothing() throws Exception {
        long before = userRepository.count();

        mvc.perform(post("/admin/users/new").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("fullName", "Impostor")
                        .param("email", "customer@dozernet.lk")
                        .param("phone", "0714440002")
                        .param("identityCardNumber", "199001010102")
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("email already exists")));

        assertThat(userRepository.count()).isEqualTo(before);
        assertThat(userRepository.findByEmail("customer@dozernet.lk").orElseThrow().hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void invalidAdminDetailsAreRejectedWithMessages() throws Exception {
        mvc.perform(post("/admin/users/new").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("fullName", "X")
                        .param("email", "not-an-email")
                        .param("phone", "123")
                        .param("identityCardNumber", "bad")
                        .param("password", "weak")
                        .param("confirmPassword", "other"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter a valid email address")));
        assertThat(userRepository.findByEmail("not-an-email")).isEmpty();
    }

    @Test
    void onlyAdminsCanCreateAdmins() throws Exception {
        mvc.perform(get("/admin/users/new").with(user("customer@dozernet.lk").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/new").with(user("customer@dozernet.lk").roles("CUSTOMER")).with(csrf())
                        .param("email", "sneaky@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/users/new")).andExpect(status().is3xxRedirection());
    }

    // ---------- customers deleting their own account ----------

    private User newCustomer(String email, String phone, String nic) {
        User customer = new User("Delete Me", email, phone, nic, passwordEncoder.encode(PASSWORD), Role.CUSTOMER);
        customer.setVerified(true);
        User saved = userRepository.save(customer);
        createdCustomerIds.add(saved.getId());
        return saved;
    }

    private Machine anyBookableMachine() {
        return machineRepository.findAll().stream().filter(Machine::isBookable).reduce((a, b) -> b).orElseThrow();
    }

    @Test
    void aCustomerWithAnOpenBookingCannotDeleteUntilItIsResolved() throws Exception {
        User customer = newCustomer("del.blocked@example.com", "0715550001", "199002020201");
        LocalDate start = LocalDate.now().plusDays(120);
        Booking booking = bookingService.create(customer, anyBookableMachine().getId(),
                start, start.plusDays(1), "Colombo", "Deletion test site");
        {
            // 1. Blocked while the booking is pending - with instructions, and nothing erased.
            mvc.perform(post("/customer/account/delete").with(user(customer.getEmail()).roles("CUSTOMER")).with(csrf())
                            .param("password", PASSWORD).param("confirm", "true"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/customer/profile"))
                    .andExpect(flash().attribute("error",
                            org.hamcrest.Matchers.containsString("finish the business you have left behind")));
            assertThat(userRepository.findById(customer.getId()).orElseThrow().isDeleted()).isFalse();

            // The profile page tells them what to finish and offers no delete form.
            mvc.perform(get("/customer/profile").with(user(customer.getEmail()).roles("CUSTOMER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("You can't delete your account yet")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("name=\"confirm\""))));

            // 2. Once the customer cancels the pending request, the block lifts.
            bookingService.cancel(customer, booking.getId());
            mvc.perform(get("/customer/profile").with(user(customer.getEmail()).roles("CUSTOMER")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"confirm\"")));

            // 3. Wrong password and a missing tick are still refused.
            mvc.perform(post("/customer/account/delete").with(user(customer.getEmail()).roles("CUSTOMER")).with(csrf())
                            .param("password", "Wrong123").param("confirm", "true"))
                    .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("password is incorrect")));
            mvc.perform(post("/customer/account/delete").with(user(customer.getEmail()).roles("CUSTOMER")).with(csrf())
                            .param("password", PASSWORD))
                    .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("tick the box")));
            assertThat(userRepository.findById(customer.getId()).orElseThrow().isDeleted()).isFalse();

            // 4. A correct, confirmed request deletes the account and ends the session.
            mvc.perform(post("/customer/account/delete").with(user(customer.getEmail()).roles("CUSTOMER")).with(csrf())
                            .param("password", PASSWORD).param("confirm", "true"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?deleted"));

            User after = userRepository.findById(customer.getId()).orElseThrow();
            assertThat(after.isDeleted()).isTrue();
            assertThat(after.isEnabled()).isFalse();
            assertThat(after.getFullName()).isEqualTo("Deleted customer");
            assertThat(after.getEmail()).endsWith("@deleted.invalid");
            assertThat(after.getEmail()).doesNotContain("del.blocked");

            // 5. The old credentials no longer work, and the details can be registered again.
            mvc.perform(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders
                            .formLogin("/login").user("email", "del.blocked@example.com").password("password", PASSWORD))
                    .andExpect(unauthenticated());
            assertThat(userRepository.existsByEmail("del.blocked@example.com")).isFalse();
            assertThat(userRepository.existsByPhone("0715550001")).isFalse();
            assertThat(userRepository.existsByIdentityCardNumber("199002020201")).isFalse();

            // 6. Booking history is preserved (still points at the anonymised account).
            assertThat(bookingRepository.findByCustomerOrderByStartDateDesc(after)).hasSize(1);
        }
    }

    @Test
    void anAdminCannotUseTheCustomerDeletionEndpoint() throws Exception {
        mvc.perform(post("/customer/account/delete").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("password", PASSWORD).param("confirm", "true"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedAccountsAreHiddenFromTheAdminUsersList() throws Exception {
        User customer = newCustomer("del.hidden@example.com", "0715550002", "199002020202");
        mvc.perform(post("/customer/account/delete").with(user(customer.getEmail()).roles("CUSTOMER")).with(csrf())
                        .param("password", PASSWORD).param("confirm", "true"))
                .andExpect(redirectedUrl("/login?deleted"));

        mvc.perform(get("/admin/users").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("deleted.invalid"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Deleted customer"))));
    }
}
