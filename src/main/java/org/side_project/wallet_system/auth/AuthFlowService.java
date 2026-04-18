package org.side_project.wallet_system.auth;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.config.SessionConstants;
import org.side_project.wallet_system.config.SessionUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthFlowService {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final MessageSource messageSource;

    // ─── Login OTP ──────────────────────────────────────────────────────────────

    public String loginOtpPage(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.PENDING_OTP))) {
            return "redirect:/login";
        }
        model.addAttribute("email", session.getAttribute(SessionConstants.OTP_EMAIL));
        model.addAttribute("otpType", "LOGIN");
        return "otp-verify";
    }

    public String verifyLoginOtp(String code, HttpSession session, RedirectAttributes redirectAttributes, Locale locale) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.PENDING_OTP))) {
            return "redirect:/login";
        }
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        try {
            authService.verifyLoginOtpCode(memberId, code);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
            return "redirect:/login/otp";
        }

        session.removeAttribute(SessionConstants.PENDING_OTP);
        session.removeAttribute(SessionConstants.OTP_EMAIL);
        authService.updateLastLogin(memberId);
        return "redirect:/dashboard";
    }

    public String resendLoginOtps(HttpSession session, RedirectAttributes redirectAttributes, Locale locale) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.PENDING_OTP))) {
            return "redirect:/login";
        }
        UUID memberId = SessionUtils.getMemberId(session);
        String email  = (String) session.getAttribute(SessionConstants.OTP_EMAIL);
        if (memberId == null || email == null) return "redirect:/login";

        authService.sendLoginOtp(memberId, email);
        redirectAttributes.addFlashAttribute("info",
                messageSource.getMessage("flash.otp.resent", null, locale));
        return "redirect:/login/otp";
    }

    // ─── Register OTP ───────────────────────────────────────────────────────────

    public String registerOtp(HttpSession session, Model model) {
        String email = (String) session.getAttribute(SessionConstants.OTP_EMAIL);
        if (email == null) return "redirect:/register";
        model.addAttribute("email", email);
        model.addAttribute("otpType", "REGISTER");
        return "otp-verify";
    }

    // ─── Register ───────────────────────────────────────────────────────────────

    public String register(String name, int age, String email, String password,
                           HttpSession session, RedirectAttributes redirectAttributes, Locale locale) {
        try {
            Member pending = authService.initiateRegistration(name, age, email, password);
            authService.sendRegistrationOtp(pending.getId(), email);
            session.setAttribute(SessionConstants.OTP_EMAIL, email);
            session.setAttribute(SessionConstants.OTP_MEMBER_ID, pending.getId().toString());
            redirectAttributes.addFlashAttribute("info",
                    messageSource.getMessage("flash.register.otp.sent", new Object[]{email}, locale));
            return "redirect:/register/otp";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
            return "redirect:/register";
        }
    }

    public String verifyRegistrationOtp(String code, HttpSession session,
                                        RedirectAttributes redirectAttributes, Locale locale) {
        String memberIdStr = (String) session.getAttribute(SessionConstants.OTP_MEMBER_ID);
        if (memberIdStr == null) return "redirect:/register";

        try {
            authService.verifyAndActivate(UUID.fromString(memberIdStr), code);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
            return "redirect:/register/otp";
        }

        session.removeAttribute(SessionConstants.OTP_EMAIL);
        session.removeAttribute(SessionConstants.OTP_MEMBER_ID);
        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("flash.register.success", null, locale));
        return "redirect:/login";
    }

    public String resendRegistrationOtp(HttpSession session, RedirectAttributes redirectAttributes, Locale locale) {
        String email       = (String) session.getAttribute(SessionConstants.OTP_EMAIL);
        String memberIdStr = (String) session.getAttribute(SessionConstants.OTP_MEMBER_ID);
        if (email == null || memberIdStr == null) return "redirect:/register";

        authService.sendRegistrationOtp(UUID.fromString(memberIdStr), email);
        redirectAttributes.addFlashAttribute("info",
                messageSource.getMessage("flash.otp.resent", null, locale));
        return "redirect:/register/otp";
    }

    // ─── Forgot / Reset Password ─────────────────────────────────────────────────

    public String sendPasswordReset(String email, RedirectAttributes redirectAttributes, Locale locale) {
        authService.initiatePasswordReset(email);
        // Always show generic message to avoid email enumeration
        redirectAttributes.addFlashAttribute("info",
                messageSource.getMessage("forgot.confirm", null, locale));
        return "redirect:/forgot-password";
    }

    public String resetPasswordPage(UUID mid, String token, Model model,
                                    RedirectAttributes redirectAttributes, Locale locale) {
        if (!passwordResetService.isValid(mid, token)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.reset.invalid", null, locale));
            return "redirect:/login";
        }
        model.addAttribute("mid", mid);
        model.addAttribute("token", token);
        return "reset-password";
    }

    public String resetPassword(UUID mid, String token, String password,
                                RedirectAttributes redirectAttributes, Locale locale) {
        if (!passwordResetService.verify(mid, token)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.reset.invalid", null, locale));
            return "redirect:/login";
        }
        authService.resetPassword(mid, password);
        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("flash.reset.success", null, locale));
        return "redirect:/login";
    }
}
