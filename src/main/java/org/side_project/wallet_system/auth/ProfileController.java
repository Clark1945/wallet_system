package org.side_project.wallet_system.auth;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.config.SessionConstants;
import org.side_project.wallet_system.config.SessionUtils;
import org.springframework.context.MessageSource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final MessageSource messageSource;

    @GetMapping("/profile")
    public String profilePage(HttpSession session, Model model) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        Member member = profileService.getMember(memberId);
        model.addAttribute("member", member);
        model.addAttribute(SessionConstants.MEMBER_NAME, SessionUtils.getMemberName(session));
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String name,
                                @RequestParam(required = false) String nickname,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String bio,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthday,
                                HttpSession session,
                                RedirectAttributes redirectAttributes,
                                Locale locale) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        try {
            profileService.updateProfile(memberId, name, nickname, phone, bio, birthday);
            session.setAttribute(SessionConstants.MEMBER_NAME, name);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("flash.profile.success", null, locale));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/avatar")
    public String updateAvatar(@RequestParam MultipartFile avatar,
                               HttpSession session,
                               RedirectAttributes redirectAttributes,
                               Locale locale) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        try {
            profileService.updateAvatar(memberId, avatar);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("flash.avatar.success", null, locale));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
        } catch (Exception e) {
            log.error("Avatar upload failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.avatar.upload.failed", null, locale));
        }
        return "redirect:/profile";
    }
}
