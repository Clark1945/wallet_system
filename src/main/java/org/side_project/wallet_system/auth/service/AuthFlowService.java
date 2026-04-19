package org.side_project.wallet_system.auth.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.objects.OtpType;
import org.side_project.wallet_system.config.SessionConstants;
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
    private final OtpService otpService;

    // ─── Login OTP ──────────────────────────────────────────────────────────────

    public String loginOtpPage(String token, Model model) {
        if (token == null) return "redirect:/login";
        try {
            UUID memberId = otpService.resolveOtpToken(token, OtpType.LOGIN);
            String email  = authService.getEmailById(memberId);
            model.addAttribute("email", email);
            model.addAttribute("otpType", "LOGIN");
            model.addAttribute("otpToken", token);
            return "otp-verify";
        } catch (IllegalArgumentException e) {
            return "redirect:/login";
        }
    }

    public String verifyLoginOtp(String token, String code, HttpSession session,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        if (token == null) return "redirect:/login";
        UUID memberId;
        try {
            memberId = otpService.resolveOtpToken(token, OtpType.LOGIN);
        } catch (IllegalArgumentException e) {
            return "redirect:/login";
        }

        try {
            authService.verifyLoginOtpCode(memberId, code);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
            return "redirect:/login/otp?token=" + token;
        }

        String memberName = authService.getNameById(memberId);
        session.setAttribute(SessionConstants.MEMBER_ID,   memberId.toString());
        session.setAttribute(SessionConstants.MEMBER_NAME, memberName);
        authService.updateLastLogin(memberId);
        return "redirect:/dashboard";
    }

    public String resendLoginOtps(String token, RedirectAttributes redirectAttributes, Locale locale) {
        if (token == null) return "redirect:/login";
        try {
            UUID memberId    = otpService.resolveOtpToken(token, OtpType.LOGIN);
            String email     = authService.getEmailById(memberId);
            authService.sendLoginOtp(memberId, email);
            String newToken  = otpService.generateOtpToken(memberId, OtpType.LOGIN);
            redirectAttributes.addFlashAttribute("info",
                    messageSource.getMessage("flash.otp.resent", null, locale));
            return "redirect:/login/otp?token=" + newToken;
        } catch (IllegalArgumentException e) {
            return "redirect:/login";
        }
    }

    // ─── Register OTP ───────────────────────────────────────────────────────────

    public String registerOtp(String token, Model model) {
        if (token == null) return "redirect:/register";
        try {
            UUID memberId = otpService.resolveOtpToken(token, OtpType.REGISTER);
            String email = authService.getEmailById(memberId);
            model.addAttribute("email", email);
            model.addAttribute("otpType", "REGISTER");
            model.addAttribute("otpToken", token);
            return "otp-verify";
        } catch (IllegalArgumentException e) {
            return "redirect:/register";
        }
    }

    // ─── Register ───────────────────────────────────────────────────────────────

    public String register(String name, int age, String email, String password,
                           RedirectAttributes redirectAttributes, Locale locale) {
        try {
            Member pending = authService.initiateRegistration(name, age, email, password);
            authService.sendRegistrationOtp(pending.getId(), email);
            String otpToken = otpService.generateOtpToken(pending.getId(),OtpType.REGISTER);
            redirectAttributes.addFlashAttribute("info",
                    messageSource.getMessage("flash.register.otp.sent", new Object[]{email}, locale));
            return "redirect:/register/otp?token=" + otpToken;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
            return "redirect:/register";
        }
    }

    public String verifyRegistrationOtp(String otpToken, String code,
                                        RedirectAttributes redirectAttributes, Locale locale) {


        try {
            String memberIdStr = otpService.resolveOtpToken(otpToken,OtpType.REGISTER).toString();
            // consume first, then verify
            boolean consumed = otpService.consumeToken(otpToken);
            if (!consumed) {
                throw new IllegalArgumentException("error.otp.invalid");
            }

            authService.verifyAndActivate(UUID.fromString(memberIdStr), code);

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
            return "redirect:/register/otp?token=" + otpToken;
        }

        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("flash.register.success", null, locale));
        return "redirect:/login";
    }

    public String resendRegistrationOtp(String token, RedirectAttributes redirectAttributes, Locale locale) {
        if (token == null) return "redirect:/register";
        try {
            UUID memberId   = otpService.resolveOtpToken(token, OtpType.REGISTER);
            String email    = authService.getEmailById(memberId);
            authService.sendRegistrationOtp(memberId, email);
            String newToken = otpService.generateOtpToken(memberId, OtpType.REGISTER);
            redirectAttributes.addFlashAttribute("info",
                    messageSource.getMessage("flash.otp.resent", null, locale));
            return "redirect:/register/otp?token=" + newToken;
        } catch (IllegalArgumentException e) {
            return "redirect:/register";
        }
    }

    // ─── Forgot / Reset Password ─────────────────────────────────────────────────

    public String sendPasswordReset(String email, RedirectAttributes redirectAttributes, Locale locale) {
        authService.initiatePasswordReset(email);
        // Always show generic message to avoid email enumeration
        redirectAttributes.addFlashAttribute("info",
                messageSource.getMessage("forgot.confirm", null, locale));
        return "redirect:/forgot-password";
    }

    public String resetPasswordPage(UUID mid, String token, HttpSession session,
                                    RedirectAttributes redirectAttributes, Locale locale) {
        if (!passwordResetService.isValid(mid, token)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.reset.invalid", null, locale));
            return "redirect:/login";
        }
        session.setAttribute(SessionConstants.RESET_MID,   mid.toString());
        session.setAttribute(SessionConstants.RESET_TOKEN, token);
        return "redirect:/reset-password/form";
    }

    public String resetPasswordForm(HttpSession session, Model model,
                                    RedirectAttributes redirectAttributes, Locale locale) {
        if (session.getAttribute(SessionConstants.RESET_MID) == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.reset.invalid", null, locale));
            return "redirect:/login";
        }
        return "reset-password";
    }

    public String resetPassword(String password, HttpSession session,
                                RedirectAttributes redirectAttributes, Locale locale) {
        String midStr = (String) session.getAttribute(SessionConstants.RESET_MID);
        String token  = (String) session.getAttribute(SessionConstants.RESET_TOKEN);
        if (midStr == null || token == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.reset.invalid", null, locale));
            return "redirect:/login";
        }
        UUID mid = UUID.fromString(midStr);
        if (!passwordResetService.verify(mid, token)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.reset.invalid", null, locale));
            return "redirect:/login";
        }
        session.removeAttribute(SessionConstants.RESET_MID);
        session.removeAttribute(SessionConstants.RESET_TOKEN);
        authService.resetPassword(mid, password);
        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("flash.reset.success", null, locale));
        return "redirect:/login";
    }
}
