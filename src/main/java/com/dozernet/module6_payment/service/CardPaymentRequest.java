package com.dozernet.module6_payment.service;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.validation.ValidationPatterns;

import java.time.YearMonth;

/**
 * Card details entered in the demo payment portal.
 *
 * <p>No bank is ever contacted, but the details are checked the way a real
 * checkout would check them - the number must pass the Luhn algorithm, the
 * expiry must still be in the future, and the CVV must be three digits - so an
 * obviously bogus card cannot settle an invoice.</p>
 *
 * @param cardholderName name printed on the card
 * @param cardNumber     digits as typed (spaces and dashes are tolerated)
 * @param expiry         expiry as printed, MM/YY
 * @param cvv            3-digit verification value
 */
public record CardPaymentRequest(String cardholderName, String cardNumber, String expiry, String cvv) {

    /** Validates every field, throwing the first problem found. */
    public void validate() {
        if (cardholderName == null || cardholderName.isBlank()) {
            throw new BusinessRuleException("Enter the cardholder name.");
        }
        validateNumber();
        validateExpiry();
        if (cvv == null || !cvv.trim().matches(ValidationPatterns.CARD_CVV)) {
            throw new BusinessRuleException(ValidationPatterns.CARD_CVV_MSG);
        }
    }

    private void validateNumber() {
        String digits = digitsOnly();
        if (digits.length() < 13 || digits.length() > 19) {
            throw new BusinessRuleException("Enter a valid card number (13 to 19 digits).");
        }
        if (!passesLuhn(digits)) {
            throw new BusinessRuleException("That card number is not valid. Please check and try again.");
        }
    }

    private void validateExpiry() {
        if (expiry == null || !expiry.trim().matches(ValidationPatterns.CARD_EXPIRY)) {
            throw new BusinessRuleException(ValidationPatterns.CARD_EXPIRY_MSG);
        }
        String[] parts = expiry.trim().split("/");
        int month = Integer.parseInt(parts[0]);
        int year = 2000 + Integer.parseInt(parts[1]);
        if (YearMonth.of(year, month).isBefore(YearMonth.now())) {
            throw new BusinessRuleException("That card has expired.");
        }
    }

    /** Last four digits, which is all we keep on the payment record. */
    public String last4() {
        String digits = digitsOnly();
        return digits.length() < 4 ? digits : digits.substring(digits.length() - 4);
    }

    private String digitsOnly() {
        return cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");
    }

    /**
     * The Luhn check digit algorithm used by every real card scheme: double
     * every second digit from the right, subtract 9 from anything over 9, and
     * the total must divide by 10.
     */
    public static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int value = digits.charAt(i) - '0';
            if (doubleDigit) {
                value *= 2;
                if (value > 9) {
                    value -= 9;
                }
            }
            sum += value;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
