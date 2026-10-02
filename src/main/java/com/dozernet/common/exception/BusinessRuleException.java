package com.dozernet.common.exception;

/**
 * Thrown when a business/validation rule is violated (e.g. overlapping booking,
 * booking a machine under maintenance). The message is safe to show the user.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
