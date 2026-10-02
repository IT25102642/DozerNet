package com.dozernet.common.validation;

import com.dozernet.module1_customer.dto.RegisterForm;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Attribute-level validation coverage for phone, email, password and shared regexes.
 */
class ValidationPatternsTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsValidSriLankanPhones() {
        Pattern p = Pattern.compile(ValidationPatterns.SL_PHONE);
        assertThat(p.matcher("0771234567").matches()).isTrue();
        assertThat(p.matcher("+94771234567").matches()).isTrue();
        assertThat(p.matcher("94771234567").matches()).isTrue();
    }

    @Test
    void rejectsInvalidPhones() {
        Pattern p = Pattern.compile(ValidationPatterns.SL_PHONE);
        assertThat(p.matcher("071234567").matches()).isFalse();      // too short
        assertThat(p.matcher("0812345678").matches()).isFalse();     // not a 07 mobile
        assertThat(p.matcher("abcdefghij").matches()).isFalse();
        assertThat(p.matcher("").matches()).isFalse();
    }

    @Test
    void acceptsValidNicFormats() {
        Pattern p = Pattern.compile(ValidationPatterns.NIC);
        assertThat(p.matcher("123456789V").matches()).isTrue();
        assertThat(p.matcher("123456789v").matches()).isTrue();
        assertThat(p.matcher("199512345678").matches()).isTrue();
    }

    @Test
    void registerFormRejectsBadEmailPhoneAndShortPassword() {
        RegisterForm form = new RegisterForm();
        form.setFullName("A");
        form.setEmail("not-an-email");
        form.setPhone("12345");
        form.setPassword("short");
        form.setConfirmPassword("short");

        Set<ConstraintViolation<RegisterForm>> violations = validator.validate(form);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("fullName", "email", "phone", "password");
    }

    @Test
    void registerFormAcceptsValidAttributes() {
        RegisterForm form = new RegisterForm();
        form.setFullName("Gayan Ananian");
        form.setEmail("gayan@example.lk");
        form.setPhone("0771234567");
        form.setIdentityCardNumber("199512345678");
        form.setPassword("Password123");
        form.setConfirmPassword("Password123");

        assertThat(validator.validate(form)).isEmpty();
    }
}
