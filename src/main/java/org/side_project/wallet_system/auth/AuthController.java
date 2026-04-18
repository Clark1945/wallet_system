package org.side_project.wallet_system.auth;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.config.SessionConstants;
import org.side_project.wallet_system.config.SessionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MessageSource messageSource;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordResetService passwordResetService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    // ─── Root ───────────────────────────────────────────────────────────────────

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    // ─── Login ──────────────────────────────────────────────────────────────────

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model, Locale locale) {
        if (error != null) {
            model.addAttribute("error",
                    messageSource.getMessage("error.login.invalid", null, locale));
        }
        return "login";
    }

    // ─── Login OTP ──────────────────────────────────────────────────────────────

    @GetMapping("/login/otp")
    public String loginOtpPage(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.PENDING_OTP))) {
            return "redirect:/login";
        }
        model.addAttribute("email", session.getAttribute(SessionConstants.OTP_EMAIL));
        model.addAttribute("otpType", "LOGIN");
        return "otp-verify";
    }

    @PostMapping("/login/otp")
    public String verifyLoginOtp(@RequestParam String code,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes,
                                 Locale locale) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.PENDING_OTP))) {
            return "redirect:/login";
        }
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        if (!otpService.verify(memberId, OtpType.LOGIN, code)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.otp.invalid", null, locale));
            return "redirect:/login/otp";
        }

        session.removeAttribute(SessionConstants.PENDING_OTP);
        session.removeAttribute(SessionConstants.OTP_EMAIL);
        authService.updateLastLogin(memberId);
        return "redirect:/dashboard";
    }

    @PostMapping("/login/otp/resend")
    public String resendLoginOtp(HttpSession session,
                                 RedirectAttributes redirectAttributes,
                                 Locale locale) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.PENDING_OTP))) {
            return "redirect:/login";
        }
        UUID memberId = SessionUtils.getMemberId(session);
        String email  = (String) session.getAttribute(SessionConstants.OTP_EMAIL);
        if (memberId == null || email == null) return "redirect:/login";

        String otp = otpService.generateAndStore(memberId, OtpType.LOGIN);
        emailService.sendLoginOtp(email, otp);
        redirectAttributes.addFlashAttribute("info",
                messageSource.getMessage("flash.otp.resent", null, locale));
        return "redirect:/login/otp";
    }

    // ─── Register ───────────────────────────────────────────────────────────────

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String name,
                           @RequestParam(defaultValue = "0") int age,
                           @RequestParam String email,
                           @RequestParam String password,
                           HttpSession session,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        try {
            Member pending = authService.initiateRegistration(name, age, email, password);
            String otp = otpService.generateAndStore(pending.getId(), OtpType.REGISTER);
            emailService.sendRegistrationOtp(email, otp);
            session.setAttribute(SessionConstants.OTP_EMAIL,     email);
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

    // ─── Register OTP ───────────────────────────────────────────────────────────

    @GetMapping("/register/otp")
    public String registrationOtpPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute(SessionConstants.OTP_EMAIL);
        if (email == null) return "redirect:/register";
        model.addAttribute("email", email);
        model.addAttribute("otpType", "REGISTER");
        return "otp-verify";
    }

    @PostMapping("/register/otp")
    public String verifyRegistrationOtp(@RequestParam String code,
                                        HttpSession session,
                                        RedirectAttributes redirectAttributes,
                                        Locale locale) {
        String memberIdStr = (String) session.getAttribute(SessionConstants.OTP_MEMBER_ID);
        if (memberIdStr == null) return "redirect:/register";

        UUID memberId = UUID.fromString(memberIdStr);
        if (!otpService.verify(memberId, OtpType.REGISTER, code)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.otp.invalid", null, locale));
            return "redirect:/register/otp";
        }

        authService.activateRegistration(memberId);
        session.removeAttribute(SessionConstants.OTP_EMAIL);
        session.removeAttribute(SessionConstants.OTP_MEMBER_ID);
        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("flash.register.success", null, locale));
        return "redirect:/login";
    }

    @PostMapping("/register/otp/resend")
    public String resendRegistrationOtp(HttpSession session,
                                        RedirectAttributes redirectAttributes,
                                        Locale locale) {
        String email       = (String) session.getAttribute(SessionConstants.OTP_EMAIL);
        String memberIdStr = (String) session.getAttribute(SessionConstants.OTP_MEMBER_ID);
        if (email == null || memberIdStr == null) return "redirect:/register";

        UUID memberId = UUID.fromString(memberIdStr);
        String otp = otpService.generateAndStore(memberId, OtpType.REGISTER);
        emailService.sendRegistrationOtp(email, otp);
        redirectAttributes.addFlashAttribute("info",
                messageSource.getMessage("flash.otp.resent", null, locale));
        return "redirect:/register/otp";
    }

    // ─── Forgot Password ────────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String sendPasswordReset(@RequestParam String email,
                                    RedirectAttributes redirectAttributes,
                                    Locale locale) {
        authService.findByEmail(email).ifPresent(member -> {
            if (member.getStatus() == MemberStatus.ACTIVE
                    && member.getAuthProvider() == AuthProvider.LOCAL) {
                String token    = passwordResetService.generateToken(member.getId());
                String resetUrl = appBaseUrl + "/reset-password?mid=" + member.getId() + "&token=" + token;
                emailService.sendPasswordResetLink(email, resetUrl);
                log.info("Password reset link sent: memberId={}", member.getId());
            }
        });
        // Always show generic message to avoid email enumeration
        redirectAttributes.addFlashAttribute("info",
                messageSource.getMessage("forgot.confirm", null, locale));
        return "redirect:/forgot-password";
    }

    // ─── Reset Password ─────────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam UUID mid,
                                    @RequestParam String token,
                                    Model model,
                                    RedirectAttributes redirectAttributes,
                                    Locale locale) {
        if (!passwordResetService.isValid(mid, token)) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.reset.invalid", null, locale));
            return "redirect:/login";
        }
        model.addAttribute("mid", mid);
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam UUID mid,
                                @RequestParam String token,
                                @RequestParam String password,
                                RedirectAttributes redirectAttributes,
                                Locale locale) {
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
