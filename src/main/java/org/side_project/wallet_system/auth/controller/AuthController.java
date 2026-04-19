package org.side_project.wallet_system.auth.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.auth.service.AuthFlowService;
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
    public String loginOtpPage(@RequestParam(required = false) String token, Model model) {
        return authFlowService.loginOtpPage(token, model);
    }

    @PostMapping("/login/otp")
    public String verifyLoginOtp(@RequestParam String token,
                                 @RequestParam String code,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes,
                                 Locale locale) {
        return authFlowService.verifyLoginOtp(token, code, session, redirectAttributes, locale);
    }

    @PostMapping("/login/otp/resend")
    public String resendLoginOtp(@RequestParam String token,
                                 RedirectAttributes redirectAttributes,
                                 Locale locale) {
        return authFlowService.resendLoginOtps(token, redirectAttributes, locale);
    }

    // ─── Register ───────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public String register(@RequestParam String name,
                           @RequestParam(defaultValue = "0") int age,
                           @RequestParam String email,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        return authFlowService.register(name, age, email, password, redirectAttributes, locale);
    }

    // ─── Register OTP ───────────────────────────────────────────────────────────

    @GetMapping("/register/otp")
    public String registrationOtpPage(@RequestParam(required = false) String token, Model model) {
        return authFlowService.registerOtp(token, model);
    }

    @PostMapping("/register/otp")
    public String verifyRegistrationOtp(@RequestParam String code,
                                        @RequestParam(value = "token") String otpToken,
                                        RedirectAttributes redirectAttributes,
                                        Locale locale) {
        return authFlowService.verifyRegistrationOtp(otpToken, code, redirectAttributes, locale);
    }

    @PostMapping("/register/otp/resend")
    public String resendRegistrationOtp(@RequestParam String token,
                                        RedirectAttributes redirectAttributes,
                                        Locale locale) {
        return authFlowService.resendRegistrationOtp(token, redirectAttributes, locale);
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
