package org.side_project.wallet_system.config;

import org.side_project.wallet_system.auth.AuthProvider;
import org.side_project.wallet_system.auth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.CustomUserDetails;
import org.side_project.wallet_system.auth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.MemberRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Dedicated filter chain for SBPS result CGI callback.
     * Must be stateless — SBPS calls server-to-server with no cookies/session.
     * SessionCreationPolicy.STATELESS prevents the session-management redirect (302)
     * that would otherwise occur when Spring Security creates a session for a
     * request arriving without a JSESSIONID cookie.
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
     * Dedicated filter chain for Stripe webhook.
     * Same rationale as the SBPS chain — Stripe calls server-to-server
     * with no session cookie. Signature verification happens inside
     * StripePaymentService via Webhook.constructEvent().
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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CustomOAuth2UserService oauth2UserService,
                                           LoginSuccessHandler loginSuccessHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/error",
                                 "/uploads/**",
                                 "/openapi.yaml", "/swagger-ui/**", "/v3/api-docs/**",
                                 "/actuator/health", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .failureUrl("/login?error")
                .successHandler(loginSuccessHandler)
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(ui -> ui.oidcUserService(oauth2UserService))
                .successHandler(loginSuccessHandler)
                .failureUrl("/login?error")
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(MemberRepository memberRepository) {
        return email -> memberRepository.findByEmail(email)
                .filter(m -> m.getAuthProvider() == AuthProvider.LOCAL)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
