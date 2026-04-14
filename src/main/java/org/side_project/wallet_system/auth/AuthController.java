package org.side_project.wallet_system.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MessageSource messageSource;

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model, Locale locale) {
        if (error != null) {
            model.addAttribute("error",
                    messageSource.getMessage("error.login.invalid", null, locale));
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String name,
                           @RequestParam(defaultValue = "0") int age,
                           @RequestParam String email,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        try {
            authService.register(name, age, email, password);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("flash.register.success", null, locale));
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
            return "redirect:/register";
        }
    }
}
