package org.side_project.wallet_system.wallet;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.payment.Transaction;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
        Wallet wallet = walletService.getWallet(memberId);
        List<Transaction> transactions = walletService.getTransactions(memberId);

        model.addAttribute("wallet", wallet);
        model.addAttribute("transactions", transactions);
        model.addAttribute("memberName", session.getAttribute("memberName"));
        return "dashboard";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam BigDecimal amount,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        try {
            UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
            walletService.deposit(memberId, amount);
            redirectAttributes.addFlashAttribute("success", "存款成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam BigDecimal amount,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        try {
            UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
            walletService.withdraw(memberId, amount);
            redirectAttributes.addFlashAttribute("success", "提款成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transfer")
    public String transfer(@RequestParam String toWalletCode,
                           @RequestParam BigDecimal amount,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        try {
            UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
            walletService.transfer(memberId, toWalletCode, amount);
            redirectAttributes.addFlashAttribute("success", "轉帳成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }
}
