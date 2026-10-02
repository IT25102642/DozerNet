package com.dozernet.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Central Spring Security configuration: BCrypt hashing plus role-based access
 * control (RBAC) for the four DozerNet roles. Public marketing/catalog pages
 * are open; each role's area is locked to that role.
 */
@Configuration
public class SecurityConfig {

    private final RoleRedirectSuccessHandler successHandler;

    public SecurityConfig(RoleRedirectSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Static assets & public marketing / catalog pages
                .requestMatchers("/", "/about", "/how-it-works",
                        "/css/**", "/js/**", "/images/**",
                        "/favicon.ico", "/error").permitAll()
                .requestMatchers("/machines", "/machines/view/**").permitAll()
                .requestMatchers("/login", "/register", "/register/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                // Role-gated areas
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/owner/**").hasRole("OWNER")
                .requestMatchers("/operator/**").hasRole("OPERATOR")
                .requestMatchers("/customer/**", "/bookings/**").hasRole("CUSTOMER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?loggedOut")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        // Allow the H2 console (dev only): it renders inside frames and posts without CSRF.
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        http.csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/h2-console/**")));

        return http.build();
    }
}
