package org.side_project.wallet_system.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * IP-based rate limiter for public auth POST endpoints.
 * Runs just after TraceIdFilter (HIGHEST_PRECEDENCE + 1), before Spring Security.
 * When a limit is exceeded the request session receives a rate-limit error attribute
 * (consumed by RateLimitModelAdvice on the next GET) and the browser is redirected
 * to the appropriate form page.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final MessageSource messageSource;

    private record RateConfig(int maxRequests, int windowSeconds, String redirectTo) {}

    private static final Map<String, RateConfig> CONFIGS = Map.ofEntries(
        Map.entry("/login",               new RateConfig(10, 60, "/login")),
        Map.entry("/register",            new RateConfig(5,  60, "/register")),
        Map.entry("/forgot-password",     new RateConfig(5,  60, "/forgot-password")),
        Map.entry("/reset-password",      new RateConfig(5,  60, "/reset-password/form")),
        Map.entry("/login/otp",           new RateConfig(10, 60, "/login")),
        Map.entry("/login/otp/resend",    new RateConfig(3,  60, "/login")),
        Map.entry("/register/otp",        new RateConfig(10, 60, "/register")),
        Map.entry("/register/otp/resend", new RateConfig(3,  60, "/register")),
        Map.entry("/register/test",       new RateConfig(3,  60, "/register"))
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        RateConfig config = CONFIGS.get(request.getRequestURI());
        if (config == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String endpoint = request.getRequestURI().substring(1).replace("/", ".");
        String rateLimitKey = endpoint + ":" + ip;

        if (!rateLimiterService.isAllowed(rateLimitKey, config.maxRequests(), Duration.ofSeconds(config.windowSeconds()))) {
            String errorMsg = messageSource.getMessage("error.rate.limit", null, request.getLocale());
            request.getSession().setAttribute(SessionConstants.RATE_LIMIT_ERROR, errorMsg);
            response.sendRedirect(request.getContextPath() + config.redirectTo());
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        // Spring's ForwardedHeaderFilter (forward-headers-strategy: framework) already
        // rewrites getRemoteAddr() to the original client IP from a trusted proxy.
        // Reading X-Forwarded-For directly would allow anyone to spoof the header.
        return request.getRemoteAddr();
    }
}
