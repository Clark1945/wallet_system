package org.side_project.wallet_system.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Propagates a rate-limit error stored in the session (by RateLimitFilter)
 * into the model so every Thymeleaf template can display it via ${error}
 * without any per-controller changes. The attribute is consumed (removed) on
 * the first GET request after the redirect, acting like a flash attribute.
 */
@ControllerAdvice
public class RateLimitModelAdvice {

    @ModelAttribute
    public void populateRateLimitError(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session == null) return;
        Object error = session.getAttribute(SessionConstants.RATE_LIMIT_ERROR);
        if (error == null) return;
        session.removeAttribute(SessionConstants.RATE_LIMIT_ERROR);
        model.addAttribute("error", error.toString());
    }
}