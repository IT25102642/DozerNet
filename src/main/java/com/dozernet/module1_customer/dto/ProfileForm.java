package com.dozernet.module1_customer.dto;

import com.dozernet.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Form for updating a customer's own profile (name and phone). Email and NIC
 * are identity fields and are not editable here.
 */
public class ProfileForm {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = ValidationPatterns.SL_PHONE, message = ValidationPatterns.SL_PHONE_MSG)
    private String phone;

    public ProfileForm() {
    }

    public ProfileForm(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
