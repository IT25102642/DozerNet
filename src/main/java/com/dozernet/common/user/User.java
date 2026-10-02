package com.dozernet.common.user;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.model.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central account for every DozerNet actor (Customer, Owner, Operator, Admin).
 * Authentication and role-based access are driven off this single table so
 * login/logout works uniformly across all six modules.
 *
 * <p>One person may hold several roles at once (for example Customer + Private
 * Owner), so roles are stored as a set. {@link #getPrimaryRole()} picks the
 * highest-privilege role, which drives the post-login landing page.</p>
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    /**
     * Sri Lankan National Identity Card number. Collected at registration and
     * never editable afterwards (shown read-only on the profile).
     * Nullable only to allow upgrading existing databases; new accounts always set it.
     */
    @Column(unique = true, length = 12)
    private String identityCardNumber;

    /** BCrypt hash - never the plain password. */
    @Column(nullable = false)
    private String passwordHash;

    /**
     * Every role this account holds. Eagerly fetched because Spring Security and
     * the view layer both read it outside an open transaction
     * ({@code spring.jpa.open-in-view=false}).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = new LinkedHashSet<>();

    /** Account active flag (admin can disable an account). */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Whether the account is verified. Customers/Admins are verified on sign-up;
     * Owners and Operators start unverified until an admin approves them.
     */
    @Column(nullable = false)
    private boolean verified = true;

    /**
     * Set when a notification could not be delivered (missing/invalid contact
     * details). The customer is prompted to update their details and admins see
     * the warning; bookings are deliberately NOT blocked.
     */
    @Column(nullable = false)
    private boolean contactUnreachable = false;

    public User() {
    }

    public User(String fullName, String email, String phone, String passwordHash, Role role) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.roles.add(role);
    }

    public User(String fullName, String email, String phone, String identityCardNumber,
                String passwordHash, Role role) {
        this(fullName, email, phone, passwordHash, role);
        this.identityCardNumber = identityCardNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getIdentityCardNumber() {
        return identityCardNumber;
    }

    public void setIdentityCardNumber(String identityCardNumber) {
        this.identityCardNumber = identityCardNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** Adds a role to an existing account (e.g. a customer also listing a machine). */
    public void addRole(Role role) {
        roles.add(role);
    }

    /**
     * Highest-privilege role held, used for the post-login landing page and for
     * displaying a single role label. Order: Admin, Operator, Owner, Customer.
     */
    public Role getPrimaryRole() {
        return roles.stream()
                .min(Comparator.comparingInt(User::priority))
                .orElse(Role.CUSTOMER);
    }

    private static int priority(Role role) {
        return switch (role) {
            case ADMIN -> 0;
            case OPERATOR -> 1;
            case OWNER -> 2;
            case CUSTOMER -> 3;
        };
    }

    /** Comma-separated role names for display, e.g. "Customer, Private JCB Owner". */
    public String getRolesLabel() {
        return roles.stream()
                .sorted(Comparator.comparingInt(User::priority))
                .map(Role::getDisplayName)
                .collect(Collectors.joining(", "));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public boolean isContactUnreachable() {
        return contactUnreachable;
    }

    public void setContactUnreachable(boolean contactUnreachable) {
        this.contactUnreachable = contactUnreachable;
    }
}
