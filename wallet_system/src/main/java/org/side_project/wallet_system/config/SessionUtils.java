package org.side_project.wallet_system.config;

import jakarta.servlet.http.HttpSession;

import java.util.UUID;

public final class SessionUtils {

    private SessionUtils() {}

    /**
     * Returns the authenticated member's UUID from the session,
     * or null if the session has expired or the attribute is missing.
     */
    public static UUID getMemberId(HttpSession session) {
        String id = (String) session.getAttribute(SessionConstants.MEMBER_ID);
        return id != null ? UUID.fromString(id) : null;
    }

    public static String getMemberName(HttpSession session) {
        return (String) session.getAttribute(SessionConstants.MEMBER_NAME);
    }
}
