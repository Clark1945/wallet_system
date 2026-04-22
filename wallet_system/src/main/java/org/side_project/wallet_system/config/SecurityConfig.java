package org.side_project.wallet_system.config;

import org.side_project.wallet_system.auth.objects.AuthProvider;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.CustomUserDetails;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

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

    /**
     * Dedicated filter chain for mock bank withdrawal webhook.
     * Called server-to-server by the mock bank — no session cookie present.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain withdrawWebhookFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/withdraw/webhook")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CustomOAuth2UserService oauth2UserService,
                                           LoginSuccessHandler loginSuccessHandler,
                                           LoginAttemptService loginAttemptService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login/otp", "/login/otp/resend",
                                 "/register", "/register/otp", "/register/otp/resend",
                                 "/forgot-password", "/reset-password", "/reset-password/form",
                                 "/error",
                                 "/uploads/**",
                                 "/openapi.yaml", "/swagger-ui/**", "/v3/api-docs/**",
                                 "/actuator/health", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .failureHandler(loginFailureHandler(loginAttemptService))
                .successHandler(loginSuccessHandler)
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(ui -> ui.oidcUserService(oauth2UserService))
                .successHandler(loginSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    if ("error.email.duplicate".equals(exception.getMessage())) {
                        response.sendRedirect("/login?oauth_conflict");
                    } else {
                        response.sendRedirect("/login?error");
                    }
                })
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .sessionManagement(s -> s.sessionFixation().migrateSession());

        return http.build();
    }

    private AuthenticationFailureHandler loginFailureHandler(LoginAttemptService loginAttemptService) {
        return (request, response, exception) -> {
            if (exception instanceof LockedException) {
                response.sendRedirect("/login?locked");
                return;
            }
            String email = request.getParameter("email");
            if (email != null && !email.isBlank()) {
                loginAttemptService.recordFailure(email.strip().toLowerCase());
            }
            response.sendRedirect("/login?error");
        };
    }

    @Bean
    public UserDetailsService userDetailsService(MemberRepository memberRepository,
                                                 LoginAttemptService loginAttemptService) {
        return email -> {
            if (loginAttemptService.isLocked(email)) {
                throw new LockedException("error.account.locked");
            }
            return memberRepository.findByEmail(email)
                    .filter(m -> m.getAuthProvider() == AuthProvider.LOCAL)
                    .map(CustomUserDetails::new)
                    .orElseThrow(() -> new UsernameNotFoundException(email));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
