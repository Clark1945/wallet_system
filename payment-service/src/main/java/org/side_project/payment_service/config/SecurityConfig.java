package org.side_project.payment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * SBPS result CGI callback — stateless, no CSRF. SBPS calls server-to-server.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain sbpaymentCallbackFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/payment/sbpayment/result")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Stripe webhook — stateless, no CSRF. Stripe calls server-to-server.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain stripeWebhookFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/payment/stripe/webhook")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Main filter chain — auth is via payment token, not Spring Security session.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain mainFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
