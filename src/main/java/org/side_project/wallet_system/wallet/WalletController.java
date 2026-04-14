package org.side_project.wallet_system.wallet;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.payment.Transaction;
import org.side_project.wallet_system.payment.TransactionType;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final MessageSource messageSource;

    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpSession session, Model model) {

        UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
        Wallet wallet = walletService.getWallet(memberId);

        TransactionType txType = (type != null && !type.isBlank()) ? TransactionType.valueOf(type) : null;
        Page<Transaction> txPage = walletService.getTransactions(memberId, txType, startDate, endDate, page, 10);

        model.addAttribute("wallet", wallet);
        model.addAttribute("txPage", txPage);
        model.addAttribute("memberName", session.getAttribute("memberName"));
        model.addAttribute("filterType", type);
        model.addAttribute("filterStart", startDate);
        model.addAttribute("filterEnd", endDate);
        return "dashboard";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam BigDecimal amount,
                          HttpSession session,
                          RedirectAttributes redirectAttributes,
                          Locale locale) {
        try {
            UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
            walletService.deposit(memberId, amount);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("flash.deposit.success", null, locale));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam BigDecimal amount,
                           HttpSession session,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        try {
            UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
            walletService.withdraw(memberId, amount);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("flash.withdraw.success", null, locale));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transfer")
    public String transfer(@RequestParam String toWalletCode,
                           @RequestParam BigDecimal amount,
                           HttpSession session,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        try {
            UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
            walletService.transfer(memberId, toWalletCode, amount);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("flash.transfer.success", null, locale));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
        }
        return "redirect:/dashboard";
    }
}
