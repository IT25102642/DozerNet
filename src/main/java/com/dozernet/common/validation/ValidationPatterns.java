package com.dozernet.common.validation;

/**
 * Shared regex patterns so every module validates the same way.
 * Used with Bean Validation {@code @Pattern} on form/DTO fields.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** Sri Lankan mobile: 07XXXXXXXX, or +947XXXXXXXX / 947XXXXXXXX. */
    public static final String SL_PHONE = "^(?:\\+94|94|0)7\\d{8}$";
    public static final String SL_PHONE_MSG = "Enter a valid Sri Lankan mobile number (e.g. 0771234567)";

    /** Sri Lankan NIC: old (9 digits + V/X) or new (12 digits). */
    public static final String NIC = "^([0-9]{9}[vVxX]|[0-9]{12})$";
    public static final String NIC_MSG = "Enter a valid NIC (9 digits + V, or 12 digits)";

    /** Vehicle/plate number, e.g. "WP ABC-1234" or "ABC-1234". */
    public static final String PLATE = "^[A-Za-z0-9]{1,3}[- ]?[A-Za-z]{0,3}[- ]?\\d{1,4}$";
    public static final String PLATE_MSG = "Enter a valid vehicle registration number";

    /** Driving licence number: alphanumeric, 5-15 chars. */
    public static final String LICENCE = "^[A-Za-z0-9]{5,15}$";
    public static final String LICENCE_MSG = "Enter a valid licence number (5-15 letters/digits)";

    /**
     * Account password policy: at least 8 characters, with at least one
     * uppercase letter and one digit.
     */
    public static final String PASSWORD = "^(?=.*[A-Z])(?=.*\\d).{8,}$";
    public static final String PASSWORD_MSG =
            "Password must be at least 8 characters and include an uppercase letter and a number";

    /** Payment card expiry as printed on the card: MM/YY. */
    public static final String CARD_EXPIRY = "^(0[1-9]|1[0-2])/\\d{2}$";
    public static final String CARD_EXPIRY_MSG = "Enter the card expiry as MM/YY";

    /** Card verification value: exactly 3 digits. */
    public static final String CARD_CVV = "^\\d{3}$";
    public static final String CARD_CVV_MSG = "CVV must be 3 digits";
}
