package org.side_project.wallet_system.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards {@code /admin/**} routes. Authorization follows the app's existing session-based model
 * (see {@link CurrentMemberArgumentResolver}): the {@link SessionConstants#IS_ADMIN} flag is stamped
 * into the session at login. A non-admin (or session-less) request is redirected to {@code /dashboard}
 * rather than 403'd, mirroring how the rest of the app degrades to the user's own area.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        Object isAdmin = session != null ? session.getAttribute(SessionConstants.IS_ADMIN) : null;
        if (Boolean.TRUE.equals(isAdmin)) {
            return true;
        }
        response.sendRedirect("/dashboard");
        return false;
    }
}
