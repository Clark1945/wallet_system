package org.side_project.wallet_system.auth.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.auth.objects.ForgotPasswordRequest;
import org.side_project.wallet_system.auth.objects.RegisterRequest;
import org.side_project.wallet_system.auth.objects.ResetPasswordRequest;
import org.side_project.wallet_system.auth.service.AuthFlowService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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
    public String register(@Valid @ModelAttribute RegisterRequest req,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error",
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/register";
        }
        return authFlowService.register(req, redirectAttributes, locale);
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
    public String sendPasswordReset(@Valid @ModelAttribute ForgotPasswordRequest req,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes,
                                    Locale locale) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error",
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/forgot-password";
        }
        return authFlowService.sendPasswordReset(req.getEmail(), redirectAttributes, locale);
    }

    // ─── Reset Password ─────────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam UUID mid,
                                    @RequestParam String token,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes,
                                    Locale locale) {
        return authFlowService.resetPasswordPage(mid, token, session, redirectAttributes, locale);
    }

    @GetMapping("/reset-password/form")
    public String resetPasswordForm(HttpSession session,
                                    Model model,
                                    RedirectAttributes redirectAttributes,
                                    Locale locale) {
        return authFlowService.resetPasswordForm(session, model, redirectAttributes, locale);
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute ResetPasswordRequest req,
                                BindingResult bindingResult,
                                HttpSession session,
                                RedirectAttributes redirectAttributes,
                                Locale locale) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error",
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/reset-password/form";
        }
        return authFlowService.resetPassword(req.getPassword(), session, redirectAttributes, locale);
    }
}
