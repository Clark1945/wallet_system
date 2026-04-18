package org.side_project.wallet_system.auth;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthFlowService authFlowService;

    // ─── Login OTP ──────────────────────────────────────────────────────────────

    @GetMapping("/login/otp")
    public String loginOtpPage(HttpSession session, Model model) {
        return authFlowService.loginOtpPage(session, model);
    }

    @PostMapping("/login/otp")
    public String verifyLoginOtp(@RequestParam String code,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes,
                                 Locale locale) {
        return authFlowService.verifyLoginOtp(code, session, redirectAttributes, locale);
    }

    @PostMapping("/login/otp/resend")
    public String resendLoginOtp(HttpSession session,
                                 RedirectAttributes redirectAttributes,
                                 Locale locale) {
        return authFlowService.resendLoginOtps(session, redirectAttributes, locale);
    }

    // ─── Register ───────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public String register(@RequestParam String name,
                           @RequestParam(defaultValue = "0") int age,
                           @RequestParam String email,
                           @RequestParam String password,
                           HttpSession session,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        return authFlowService.register(name, age, email, password, session, redirectAttributes, locale);
    }

    // ─── Register OTP ───────────────────────────────────────────────────────────

    @GetMapping("/register/otp")
    public String registrationOtpPage(HttpSession session, Model model) {
        return authFlowService.registerOtp(session, model);
    }

    @PostMapping("/register/otp")
    public String verifyRegistrationOtp(@RequestParam String code,
                                        HttpSession session,
                                        RedirectAttributes redirectAttributes,
                                        Locale locale) {
        return authFlowService.verifyRegistrationOtp(code, session, redirectAttributes, locale);
    }

    @PostMapping("/register/otp/resend")
    public String resendRegistrationOtp(HttpSession session,
                                        RedirectAttributes redirectAttributes,
                                        Locale locale) {
        return authFlowService.resendRegistrationOtp(session, redirectAttributes, locale);
    }

    // ─── Forgot Password ────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    public String sendPasswordReset(@RequestParam String email,
                                    RedirectAttributes redirectAttributes,
                                    Locale locale) {
        return authFlowService.sendPasswordReset(email, redirectAttributes, locale);
    }

    // ─── Reset Password ─────────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam UUID mid,
                                    @RequestParam String token,
                                    Model model,
                                    RedirectAttributes redirectAttributes,
                                    Locale locale) {
        return authFlowService.resetPasswordPage(mid, token, model, redirectAttributes, locale);
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam UUID mid,
                                @RequestParam String token,
                                @RequestParam String password,
                                RedirectAttributes redirectAttributes,
                                Locale locale) {
        return authFlowService.resetPassword(mid, token, password, redirectAttributes, locale);
    }
}
