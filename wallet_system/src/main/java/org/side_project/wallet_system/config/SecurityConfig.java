package org.side_project.wallet_system.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.side_project.wallet_system.auth.objects.AuthProvider;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.CustomUserDetails;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${internal.service.secret}")
    private String internalServiceSecret;

    /**
     * Dedicated filter chain for internal service-to-service API.
     * Validates X-Internal-Secret header; rejects with 401 if absent or wrong.
     * payment-service is the only authorized caller.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/internal/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(internalSecretFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    private OncePerRequestFilter internalSecretFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String header = request.getHeader("X-Internal-Secret");
                if (!internalServiceSecret.equals(header)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }

    /**
     * Dedicated filter chain for mock bank withdrawal webhook.
     * Called server-to-server by the mock bank — no session cookie present.
     */
    @Bean
    @Order(2)
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
                                 "/register", "/register/otp", "/register/otp/resend", "/register/test",
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
