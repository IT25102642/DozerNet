package com.dozernet.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minor function: role-based redirection after login. Each role lands on its
 * own dashboard rather than a generic page.
 */
@Component
public class RoleRedirectSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        String target = "/";
        if (roles.contains("ROLE_ADMIN")) {
            target = "/admin/dashboard";
        } else if (roles.contains("ROLE_OWNER")) {
            target = "/owner/dashboard";
        } else if (roles.contains("ROLE_OPERATOR")) {
            target = "/operator/dashboard";
        } else if (roles.contains("ROLE_CUSTOMER")) {
            target = "/customer/dashboard";
        }

        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
