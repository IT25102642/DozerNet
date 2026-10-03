package com.dozernet.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Renders friendly error pages instead of stack traces. Business-rule failures
 * inside forms are handled locally in controllers via flash messages; this is
 * the safety net for anything that bubbles up.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("code", 404);
        model.addAttribute("message", ex.getMessage());
        return "error/generic";
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusinessRule(BusinessRuleException ex, Model model) {
        model.addAttribute("code", 400);
        model.addAttribute("message", ex.getMessage());
        return "error/generic";
    }
}
